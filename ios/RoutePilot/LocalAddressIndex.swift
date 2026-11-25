import Foundation

enum LocalIndexError: Error {
  case unavailable
  case invalidIndex
}

final class LocalAddressIndex {
  static let shared = LocalAddressIndex()

  private let versionURL = URL(string: "https://raw.githubusercontent.com/wandanny/RoutePilot/main/ios/data/taiping-addresses.version")!
  private let indexURL = URL(string: "https://raw.githubusercontent.com/wandanny/RoutePilot/main/ios/data/taiping-addresses.jsonl")!
  private let versionKey = "routepilot.index.version"
  private let checkKey = "routepilot.index.lastCheck"

  private var entries: [AddressEntry] = []

  private var documentsIndexURL: URL? {
    FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?
      .appendingPathComponent("taiping-addresses.jsonl")
  }

  init() { loadIfNeeded() }

  func loadIfNeeded() {
    guard entries.isEmpty else { return }
    let url = documentsIndexURL ?? Bundle.main.url(forResource: "taiping-addresses", withExtension: "jsonl")
    guard let url, let text = try? String(contentsOf: url, encoding: .utf8) else { return }
    entries = text.split(separator: Character("\n")).compactMap { line in
      guard let data = line.data(using: .utf8) else { return nil }
      return try? JSONDecoder().decode(AddressEntry.self, from: data)
    }
  }

  var count: Int { entries.count }

  func lookup(_ address: String) -> (Double, Double)? {
    loadIfNeeded()
    let key = AddressNormalizer.key(address)
    guard !key.isEmpty else { return nil }
    let tokens = key.split(separator: Character(" ")).map(String.init)
    let hasHouseNumber = tokens.contains { $0.first?.isNumber == true }
    let expectedPostcode = AddressNormalizer.postcode(address)

    var matches: [AddressEntry] = []
    for entry in entries {
      if let expectedPostcode, !entry.p.isEmpty, entry.p != expectedPostcode { continue }
      if hasHouseNumber {
        let entryTokens = Set(entry.a.split(separator: Character(" ")).map(String.init))
        if !entryTokens.isEmpty, tokens.allSatisfy({ entryTokens.contains($0) }) {
          matches.append(entry)
        }
      } else if !entry.n.isEmpty, entry.n == key {
        matches.append(entry)
      }
      if matches.count > 1 { return nil }
    }
    guard matches.count == 1 else { return nil }
    return (matches[0].y, matches[0].x)
  }

  @discardableResult
  func refreshIfNeeded(force: Bool) async -> Bool {
    let lastCheck = UserDefaults.standard.object(forKey: checkKey) as? Date ?? .distantPast
    UserDefaults.standard.set(Date(), forKey: checkKey)
    if !force, Date().timeIntervalSince(lastCheck) < 7 * 24 * 60 * 60 { return false }

    guard let (remoteVersionData, _) = try? await URLSession.shared.data(from: versionURL),
          let remoteVersion = String(data: remoteVersionData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
          !remoteVersion.isEmpty else { return false }
    let localVersion = UserDefaults.standard.string(forKey: versionKey) ?? ""
    if !force, remoteVersion == localVersion { return false }

    guard let (data, _) = try? await URLSession.shared.data(from: indexURL),
          let text = String(data: data, encoding: .utf8),
          let destination = documentsIndexURL else { return false }
    let decoded = text.split(separator: Character("\n")).compactMap { line -> AddressEntry? in
      guard let lineData = line.data(using: .utf8) else { return nil }
      return try? JSONDecoder().decode(AddressEntry.self, from: lineData)
    }
    guard !decoded.isEmpty, (try? text.write(to: destination, atomically: true, encoding: .utf8)) != nil else { return false }
    entries = decoded
    UserDefaults.standard.set(remoteVersion, forKey: versionKey)
    return true
  }

  var hasDownloadedIndex: Bool {
    guard let url = documentsIndexURL else { return false }
    return FileManager.default.fileExists(atPath: url.path)
  }
}
