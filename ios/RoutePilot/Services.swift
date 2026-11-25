import Foundation
import UIKit
import Security
import Vision
import UserNotifications
import CoreLocation

// MARK: - Secure settings

enum KeychainStore {
  static func save(_ value: String, key: String) {
    guard let data = value.data(using: .utf8) else { return }
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
      kSecValueData as String: data,
    ]
    SecItemDelete(query as CFDictionary)
    SecItemAdd(query as CFDictionary, nil)
  }

  static func read(key: String) -> String {
    let query: [String: Any] = [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrAccount as String: key,
      kSecReturnData as String: true,
      kSecMatchLimit as String: kSecMatchLimitOne,
    ]
    var item: CFTypeRef?
    guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
          let data = item as? Data,
          let value = String(data: data, encoding: .utf8) else { return "" }
    return value
  }
}

// MARK: - Parsers

enum LabelParser {
  struct Result {
    var name: String = ""
    var address: String = ""
    var postcode: String = ""
    var phone: String = ""
    var warnings: [String] = []
  }

  static func parse(_ text: String) -> Result {
    let lines = text.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    let recipientIndex = lines.firstIndex {
      $0.localizedCaseInsensitiveContains("Recipient") || $0.localizedCaseInsensitiveContains("Penerima")
    }
    let start = recipientIndex.map { $0 + 1 }
      ?? lines.firstIndex { $0.hasPrefix("Address:") }
      ?? lines.endIndex
    var result = Result()
    var i = start
    while i < lines.count {
      let line = lines[i]
      if result.name.isEmpty, line.hasPrefix("Name:") {
        result.name = String(line.dropFirst("Name:".count)).trimmingCharacters(in: .whitespaces)
      }
      if result.address.isEmpty, line.hasPrefix("Address:") {
        var parts = [String(line.dropFirst("Address:".count)).trimmingCharacters(in: .whitespaces)]
        var j = i + 1
        while j < lines.count, !lines[j].hasPrefix("Postcode:"), !lines[j].hasPrefix("Name:"),
              !lines[j].localizedCaseInsensitiveContains("Details") {
          parts.append(lines[j])
          j += 1
        }
        result.address = parts.joined(separator: ", ")
        if j < lines.count, lines[j].hasPrefix("Postcode:") {
          result.postcode = digits(lines[j], length: 5)
        }
      }
      i += 1
    }
    if result.postcode.isEmpty {
      result.postcode = digits(text, length: 5)
      if !result.postcode.isEmpty { result.warnings.append("Postcode inferred") }
    }
    result.phone = phoneNumber(text)
    if result.address.isEmpty { result.warnings.append("No address found") }
    if result.name.isEmpty { result.warnings.append("No recipient name found") }
    return result
  }

  private static func digits(_ text: String, length: Int) -> String {
    let numbers = text.components(separatedBy: CharacterSet.decimalDigits.inverted).filter { $0.count == length }
    return numbers.first ?? ""
  }

  private static func phoneNumber(_ text: String) -> String {
    let pattern = #"(?:\+?60)?0\d{1,2}[- ]?\d{3,4}[- ]?\d{3,4}"#
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return "" }
    let range = NSRange(text.startIndex..., in: text)
    guard let match = regex.firstMatch(in: text, range: range),
          let found = Range(match.range, in: text) else { return "" }
    return String(text[found])
  }
}

enum CsvParser {
  static func parse(_ text: String) -> [[String: String]] {
    let lines = text.split(separator: "\n").map { String($0).trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    guard let first = lines.first else { return [] }
    let delimiter: Character = first.filter { $0 == ";" }.count > first.filter { $0 == "," }.count ? ";" : ","
    let rows = lines.map { split($0, delimiter) }
    let headers = rows[0].map { $0.lowercased() }
    return rows.dropFirst().map { cells in
      Dictionary(uniqueKeysWithValues: zip(headers, cells))
    }
  }

  private static func split(_ line: String, _ delimiter: Character) -> [String] {
    var result: [String] = []
    var current = ""
    var quoted = false
    for ch in line {
      if ch == "\"" { quoted.toggle() }
      else if ch == delimiter && !quoted {
        result.append(current)
        current = ""
      } else { current.append(ch) }
    }
    result.append(current)
    return result
  }
}

// MARK: - On-device OCR

enum DeviceOCR {
  static func fields(from image: UIImage) async -> LabelParser.Result {
    guard let cgImage = image.cgImage else {
      return LabelParser.Result(warnings: ["Could not read the image"])
    }
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.usesLanguageCorrection = true
    let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
    do {
      try handler.perform([request])
      let observations = request.results ?? []
      let text = observations.compactMap { $0.topCandidates(1).first?.string }.joined(separator: "\n")
      guard !text.isEmpty else { return LabelParser.Result(warnings: ["No text found on the label"]) }
      return LabelParser.parse(text)
    } catch {
      return LabelParser.Result(warnings: ["On-device reading failed — enter the address manually"])
    }
  }
}

// MARK: - Notifications

enum NotificationScheduler {
  static func requestPermission() async -> Bool {
    let center = UNUserNotificationCenter.current()
    let settings = await center.notificationSettings()
    if settings.authorizationStatus != .notDetermined {
      if settings.authorizationStatus == .authorized { configure() }
      return settings.authorizationStatus == .authorized
    }
    guard let granted = try? await center.requestAuthorization(options: [.alert, .sound, .badge]) else { return false }
    if granted { configure() }
    return granted
  }

  static func configure() {
    let category = UNNotificationCategory(
      identifier: "deliveryWindow", actions: [], intentIdentifiers: [], options: []
    )
    UNUserNotificationCenter.current().setNotificationCategories([category])
  }

  static func schedule(label: String, windowStart: String, stopID: String) {
    guard let time = parseTime(windowStart) else { return }
    let now = Date()
    guard let window = Calendar.current.date(bySettingHour: time.hour, minute: time.minute, second: 0, of: now),
          let fireDate = Calendar.current.date(byAdding: .minute, value: -30, to: window) else { return }
    var fire = fireDate
    if fire <= now { fire = Calendar.current.date(byAdding: .day, value: 1, to: fire) ?? now }
    let trigger = UNCalendarNotificationTrigger(
      dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire),
      repeats: false
    )
    let content = UNMutableNotificationContent()
    content.title = "Delivery window"
    content.body = "\(label) opens at \(windowStart)."
    content.sound = .default
    content.categoryIdentifier = "deliveryWindow"
    let request = UNNotificationRequest(identifier: "window-\(stopID)", content: content, trigger: trigger)
    UNUserNotificationCenter.current().add(request)
  }

  static func cancel(stopID: String) {
    UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["window-\(stopID)"])
  }

  private static func parseTime(_ value: String) -> (hour: Int, minute: Int)? {
    let parts = value.split(separator: ":").map { Int($0) }
    guard parts.count == 2, let hour = parts[0], let minute = parts[1],
          (0...23).contains(hour), (0...59).contains(minute) else { return nil }
    return (hour, minute)
  }
}

// MARK: - AI client

private struct ChatResponse: Decodable {
  struct Choice: Decodable { struct Message: Decodable { var content: String }; var message: Message }
  var choices: [Choice]
}

enum LucifClient {
  static let endpoint = URL(string: "https://lucif.ai/v1/chat/completions")!

  static func fields(from image: UIImage) async throws -> LabelParser.Result {
    let key = KeychainStore.read(key: "lucif_api_key")
    guard !key.isEmpty else {
      throw NSError(domain: "RoutePilot", code: 1,
        userInfo: [NSLocalizedDescriptionKey: "Set an API key in AI settings"])
    }
    guard let data = image.jpegData(compressionQuality: 0.8) else {
      throw NSError(domain: "RoutePilot", code: 2)
    }
    let base64 = data.base64EncodedString()
    let prompt = "Read this parcel label. Return ONLY compact JSON: {\"name\":...,\"address\":...,\"postcode\":...,\"phone\":...} for the RECIPIENT, not the sender. Use null if unknown."
    let body: [String: Any] = [
      "model": "lucif",
      "max_tokens": 250,
      "messages": [["role": "user", "content": [
        ["type": "text", "text": prompt],
        ["type": "image_url", "image_url": ["url": "data:image/jpeg;base64," + base64]],
      ]]],
    ]
    let content = try await chat(body, key: key)
    return decodeFields(content)
  }

  private static func chat(_ body: [String: Any], key: String) async throws -> String {
    var request = URLRequest(url: endpoint)
    request.httpMethod = "POST"
    request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = try JSONSerialization.data(withJSONObject: body)
    let (data, _) = try await URLSession.shared.data(for: request)
    let decoded = try JSONDecoder().decode(ChatResponse.self, from: data)
    return decoded.choices.first?.message.content ?? ""
  }

  private static func decodeFields(_ raw: String) -> LabelParser.Result {
    guard let json = extractJSON(raw) else { return LabelParser.Result(warnings: ["No JSON in AI response"]) }
    return LabelParser.Result(
      name: json["name"] as? String ?? "",
      address: json["address"] as? String ?? "",
      postcode: json["postcode"] as? String ?? "",
      phone: json["phone"] as? String ?? "",
    )
  }

  private static func extractJSON(_ raw: String) -> [String: Any]? {
    guard let start = raw.firstIndex(of: "{"), let end = raw.lastIndex(of: "}") else { return nil }
    let slice = String(raw[start...end])
    guard let data = slice.data(using: .utf8) else { return nil }
    return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
  }
}

// MARK: - Geocoding and routing

enum AddressSupport {
  static let maxAreaDistance: CLLocationDistance = 50_000

  static func isPlausible(latitude: Double, longitude: Double, near origin: (Double, Double)?) -> Bool {
    guard let origin else { return true }
    let point = CLLocation(latitude: latitude, longitude: longitude)
    let centre = CLLocation(latitude: origin.0, longitude: origin.1)
    return point.distance(from: centre) <= maxAreaDistance
  }
}

enum AppleGeocoder {
  static func geocode(_ address: String) async -> (Double, Double)? {
    let geocoder = CLGeocoder()
    guard let placemark = try? await geocoder.geocodeAddressString(address),
          let location = placemark.first?.location else { return nil }
    return (location.coordinate.latitude, location.coordinate.longitude)
  }
}

enum Nominatim {
  static func geocode(_ address: String, structured: Bool = false) async -> (Double, Double)? {
    var components = URLComponents(string: "https://nominatim.openstreetmap.org/search")!
    var items = [
      URLQueryItem(name: "format", value: "json"),
      URLQueryItem(name: "limit", value: "1"),
    ]
    if structured {
      items += [
        URLQueryItem(name: "street", value: address),
        URLQueryItem(name: "city", value: "Taiping"),
        URLQueryItem(name: "state", value: "Perak"),
        URLQueryItem(name: "country", value: "Malaysia"),
      ]
    } else {
      items.append(URLQueryItem(name: "q", value: address))
    }
    components.queryItems = items
    guard let url = components.url else { return nil }
    var request = URLRequest(url: url)
    request.setValue("RoutePilot/1.0 (courier route helper)", forHTTPHeaderField: "User-Agent")
    guard let (data, _) = try? await URLSession.shared.data(for: request),
          let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]],
          let first = array.first else { return nil }
    let lat = (first["lat"] as? String).flatMap { Double($0) }
    let lon = (first["lon"] as? String).flatMap { Double($0) }
    guard let lat, let lon else { return nil }
    return (lat, lon)
  }
}

enum Geocoder {
  static func resolve(_ address: String, restrictToArea: Bool, origin: (Double, Double)?) async -> (Double, Double)? {
    let acceptable: (Double, Double) -> Bool = { lat, lon in
      !restrictToArea || AddressSupport.isPlausible(latitude: lat, longitude: lon, near: origin)
    }
    if let point = await TomTomClient.geocode(address, restrictToArea: restrictToArea, origin: origin),
       acceptable(point.0, point.1) { return point }
    if let point = await AppleGeocoder.geocode(address), acceptable(point.0, point.1) { return point }
    if let point = await Nominatim.geocode(address, structured: restrictToArea), acceptable(point.0, point.1) { return point }
    return nil
  }
}

enum TomTomClient {
  private static let pathAllowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~,:()&"))

  static var hasKey: Bool { !KeychainStore.read(key: "tomtom_api_key").isEmpty }

  static func geocode(_ address: String, restrictToArea: Bool, origin: (Double, Double)?) async -> (Double, Double)? {
    let key = KeychainStore.read(key: "tomtom_api_key")
    guard !key.isEmpty,
          let encoded = address.addingPercentEncoding(withAllowedCharacters: pathAllowed),
          var components = URLComponents(string: "https://api.tomtom.com/search/2/geocode/\(encoded).json") else { return nil }
    var items = [
      URLQueryItem(name: "key", value: key),
      URLQueryItem(name: "limit", value: "1"),
      URLQueryItem(name: "countrySet", value: "MY"),
    ]
    if restrictToArea, let origin {
      items += [
        URLQueryItem(name: "lat", value: String(origin.0)),
        URLQueryItem(name: "lon", value: String(origin.1)),
        URLQueryItem(name: "radius", value: "50000"),
      ]
    }
    components.queryItems = items
    guard let data = await fetch(components),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let results = json["results"] as? [[String: Any]],
          let first = results.first,
          let position = first["position"] as? [String: Any],
          let lat = position["lat"] as? Double,
          let lon = position["lon"] as? Double else { return nil }
    return (lat, lon)
  }

  static func table(origin: (Double, Double), stops: [(Double, Double)]) async -> [[Double]]? {
    let key = KeychainStore.read(key: "tomtom_api_key")
    guard !key.isEmpty, var components = URLComponents(string: "https://api.tomtom.com/routing/1/matrix/json") else { return nil }
    let locations = ([origin] + stops).map { "\($0.0),\($0.1)" }.joined(separator: ":")
    components.queryItems = [
      URLQueryItem(name: "key", value: key),
      URLQueryItem(name: "locations", value: locations),
      URLQueryItem(name: "travelMode", value: "car"),
      URLQueryItem(name: "routeType", value: "fastest"),
    ]
    guard let data = await fetch(components),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let matrices = json["matrices"] as? [[String: Any]],
          let durations = matrices.first?["durationsInSeconds"] as? [[Double]],
          !durations.isEmpty else { return nil }
    return durations
  }

  static func route(origin: (Double, Double), stops: [(Double, Double)]) async -> RoutePath? {
    let key = KeychainStore.read(key: "tomtom_api_key")
    let coords = ([origin] + stops).map { "\($0.0),\($0.1)" }.joined(separator: ":")
    guard !key.isEmpty,
          let path = coords.addingPercentEncoding(withAllowedCharacters: pathAllowed),
          var components = URLComponents(string: "https://api.tomtom.com/routing/1/calculateRoute/\(path)/json") else { return nil }
    components.queryItems = [
      URLQueryItem(name: "key", value: key),
      URLQueryItem(name: "travelMode", value: "car"),
      URLQueryItem(name: "routeType", value: "fastest"),
    ]
    guard let data = await fetch(components),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let routes = json["routes"] as? [[String: Any]],
          let first = routes.first else { return nil }
    var points: [(Double, Double)] = []
    if let legs = first["legs"] as? [[String: Any]] {
      for leg in legs {
        if let coords = leg["points"] as? [[String: Any]] {
          for point in coords {
            if let lat = point["latitude"] as? Double, let lon = point["longitude"] as? Double {
              points.append((lat, lon))
            }
          }
        }
      }
    }
    let summary = first["summary"] as? [String: Any]
    return RoutePath(
      points: points,
      distanceKm: (summary?["lengthInMeters"] as? Double ?? 0) / 1000,
      durationMin: (summary?["travelTimeInSeconds"] as? Double ?? 0) / 60
    )
  }

  private static func fetch(_ components: URLComponents) async -> Data? {
    guard let url = components.url,
          let (data, _) = try? await URLSession.shared.data(for: URLRequest(url: url)) else { return nil }
    return data
  }
}

struct RoutePath {
  var points: [(Double, Double)] = []
  var distanceKm: Double = 0
  var durationMin: Double = 0
}

enum Osrm {
  static func route(origin: (Double, Double), stops: [(Double, Double)]) async -> RoutePath? {
    let coords = [origin] + stops
    let path = coords.map { "\($0.1),\($0.0)" }.joined(separator: ";")
    guard let url = URL(string: "https://router.project-osrm.org/route/v1/driving/\(path)?overview=full&geometries=geojson&steps=false"),
          let (data, _) = try? await URLSession.shared.data(for: URLRequest(url: url)),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let routes = json["routes"] as? [[String: Any]],
          let first = routes.first else { return nil }
    var points: [(Double, Double)] = []
    if let geometry = first["geometry"] as? [String: Any],
       let coordinates = geometry["coordinates"] as? [[Double]] {
      for pair in coordinates where pair.count >= 2 {
        points.append((pair[1], pair[0]))
      }
    }
    return RoutePath(
      points: points,
      distanceKm: (first["distance"] as? Double ?? 0) / 1000,
      durationMin: (first["duration"] as? Double ?? 0) / 60,
    )
  }

  static func table(origin: (Double, Double), stops: [(Double, Double)]) async -> [[Double]]? {
    let coords = [origin] + stops
    let path = coords.map { "\($0.1),\($0.0)" }.joined(separator: ";")
    guard let url = URL(string: "https://router.project-osrm.org/table/v1/driving/\(path)?annotations=duration"),
          let (data, _) = try? await URLSession.shared.data(for: URLRequest(url: url)),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let durations = json["durations"] as? [[Double]] else { return nil }
    return durations
  }
}

enum RouteSolver {
  static func order(matrix: [[Double]], stopCount: Int) -> [Int] {
    guard stopCount > 0 else { return [] }
    func cost(_ from: Int, _ to: Int) -> Double {
      guard matrix.indices.contains(from), matrix[from].indices.contains(to) else { return Double.greatestFiniteMagnitude / 4 }
      return matrix[from][to]
    }
    var best: [Int] = []
    var bestCost = Double.greatestFiniteMagnitude
    for last in 1...stopCount {
      var remaining = Array(1...stopCount).filter { $0 != last }
      var order: [Int] = []
      var current = 0
      while !remaining.isEmpty {
        guard let next = remaining.min(by: { cost(current, $0) < cost(current, $1) }) else { break }
        order.append(next)
        remaining.removeAll { $0 == next }
        current = next
      }
      order.append(last)
      var total = 0.0
      var at = 0
      for next in order {
        total += cost(at, next)
        at = next
      }
      if total < bestCost { bestCost = total; best = order }
    }
    return best
  }
}
