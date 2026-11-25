import Foundation
import UIKit

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
    var warnings: [String] = []
  }

  static func parse(_ text: String) -> Result {
    let lines = text.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    let recipientIndex = lines.firstIndex { $0.localizedCaseInsensitiveContains("Recipient") || $0.localizedCaseInsensitiveContains("Penerima") }
    let start = recipientIndex.map { $0 + 1 } ?? lines.firstIndex { $0.hasPrefix("Address:") } ?? lines.endIndex
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
    if result.address.isEmpty { result.warnings.append("No address found") }
    if result.name.isEmpty { result.warnings.append("No recipient name found") }
    return result
  }

  private static func digits(_ text: String, length: Int) -> String {
    let numbers = text.components(separatedBy: CharacterSet.decimalDigits.inverted).filter { $0.count == length }
    return numbers.first ?? ""
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

// MARK: - Network

private struct ChatResponse: Decodable {
  struct Choice: Decodable { struct Message: Decodable { var content: String }; var message: Message }
  var choices: [Choice]
}

enum LucifClient {
  static let endpoint = URL(string: "https://lucif.ai/v1/chat/completions")!

  static func fields(from image: UIImage) async throws -> LabelParser.Result {
    let key = KeychainStore.read(key: "lucif_api_key")
    guard !key.isEmpty else { throw NSError(domain: "RoutePilot", code: 1, userInfo: [NSLocalizedDescriptionKey: "Set an API key in AI settings"]) }
    guard let data = image.jpegData(compressionQuality: 0.8) else { throw NSError(domain: "RoutePilot", code: 2) }
    let base64 = data.base64EncodedString()
    let prompt = "Read this parcel label. Return ONLY compact JSON: {\"name\":...,\"address\":...,\"postcode\":...} for the RECIPIENT, not the sender. Use null if unknown."
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

  static func coordinates(for address: String) async throws -> (Double, Double)? {
    let key = KeychainStore.read(key: "lucif_api_key")
    guard !key.isEmpty else { return nil }
    let body: [String: Any] = [
      "model": "lucif",
      "max_tokens": 120,
      "messages": [["role": "user", "content": "Estimate latitude and longitude for: \(address). Return ONLY JSON {\"lat\":number,\"lon\":number}"]],
    ]
    let content = try await chat(body, key: key)
    guard let json = extractJSON(content),
          let lat = json["lat"] as? Double ?? (json["lat"] as? String).flatMap(Double.init),
          let lon = json["lon"] as? Double ?? (json["lon"] as? String).flatMap(Double.init) else { return nil }
    return (lat, lon)
  }

  private static func chat(_ body: [String: Any], key: String) async throws -> String {
    var request = URLRequest(url: endpoint)
    request.httpMethod = "POST"
    request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = try JSONSerialization.data(withJSONObject: body)
    let (data, _) = try await URLSession.shared.dataTask(for: request)
    let decoded = try JSONDecoder().decode(ChatResponse.self, from: data)
    return decoded.choices.first?.message.content ?? ""
  }

  private static func decodeFields(_ raw: String) -> LabelParser.Result {
    guard let json = extractJSON(raw) else { return LabelParser.Result(warnings: ["No JSON in AI response"]) }
    return LabelParser.Result(
      name: json["name"] as? String ?? "",
      address: json["address"] as? String ?? "",
      postcode: json["postcode"] as? String ?? "",
    )
  }

  private static func extractJSON(_ raw: String) -> [String: Any]? {
    guard let start = raw.firstIndex(of: "{"), let end = raw.lastIndex(of: "}") else { return nil }
    let slice = String(raw[start...end])
    guard let data = slice.data(using: .utf8) else { return nil }
    return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
  }
}

enum Nominatim {
  static func geocode(_ address: String) async -> (Double, Double)? {
    guard let query = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
          let url = URL(string: "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=\(query)") else { return nil }
    var request = URLRequest(url: url)
    request.setValue("RoutePilot/1.0 (courier route helper)", forHTTPHeaderField: "User-Agent")
    guard let (data, _) = try? await URLSession.shared.dataTask(for: request),
          let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]],
          let first = array.first,
          let lat = Double(first["lat"] as? String ?? ""),
          let lon = Double(first["lon"] as? String ?? "") else { return nil }
    return (lat, lon)
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
    guard let url = URL(string: "https://router.project-osrm.org/route/v1/driving/\(path)?overview=full&geometries=geojson&steps=false") else { return nil }
    guard let (data, _) = try? await URLSession.shared.dataTask(for: URLRequest(url: url)),
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
    guard let url = URL(string: "https://router.project-osrm.org/table/v1/driving/\(path)?annotations=duration") else { return nil }
    guard let (data, _) = try? await URLSession.shared.dataTask(for: URLRequest(url: url)),
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
      let total = order.reduce((cost: 0.0, at: 0)) { acc, next in
        (acc.cost + cost(acc.at, next), next)
      }.cost
      if total < bestCost { bestCost = total; best = order }
    }
    return best
  }
}
