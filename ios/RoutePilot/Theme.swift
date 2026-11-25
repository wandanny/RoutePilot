import SwiftUI

enum RP {
  enum Light {
    static let canvas = Color(hex: "F7F5F1")
    static let surface = Color.white
    static let surfaceMuted = Color(hex: "EDE9E2")
    static let charcoal = Color(hex: "262625")
    static let textPrimary = Color(hex: "1F1D1A")
    static let textSecondary = Color(hex: "6B6560")
    static let accentFill = Color(hex: "F97316")
    static let accentText = Color(hex: "C2410C")
    static let onAccent = Color(hex: "1F1D1A")
    static let error = Color(hex: "B3261E")
    static let success = Color(hex: "2E7D32")
  }

  enum Dark {
    static let canvas = Color(hex: "151514")
    static let surface = Color(hex: "1E1E1D")
    static let surfaceMuted = Color(hex: "2A2927")
    static let charcoal = Color(hex: "101010")
    static let textPrimary = Color(hex: "F7F5F1")
    static let textSecondary = Color(hex: "B9B2AA")
    static let accentFill = Color(hex: "F97316")
    static let accentText = Color(hex: "F97316")
    static let onAccent = Color(hex: "1F1D1A")
    static let error = Color(hex: "FFB4AB")
    static let success = Color(hex: "81C784")
  }

  static func canvas(_ s: ColorScheme) -> Color { s == .dark ? Dark.canvas : Light.canvas }
  static func surface(_ s: ColorScheme) -> Color { s == .dark ? Dark.surface : Light.surface }
  static func surfaceMuted(_ s: ColorScheme) -> Color { s == .dark ? Dark.surfaceMuted : Light.surfaceMuted }
  static func charcoal(_ s: ColorScheme) -> Color { s == .dark ? Dark.charcoal : Light.charcoal }
  static func text(_ s: ColorScheme) -> Color { s == .dark ? Dark.textPrimary : Light.textPrimary }
  static func secondary(_ s: ColorScheme) -> Color { s == .dark ? Dark.textSecondary : Light.textSecondary }
  static func accentFill(_ s: ColorScheme) -> Color { s == .dark ? Dark.accentFill : Light.accentFill }
  static func accentText(_ s: ColorScheme) -> Color { s == .dark ? Dark.accentText : Light.accentText }
  static func onAccent(_ s: ColorScheme) -> Color { s == .dark ? Dark.onAccent : Light.onAccent }
  static func error(_ s: ColorScheme) -> Color { s == .dark ? Dark.error : Light.error }
  static func success(_ s: ColorScheme) -> Color { s == .dark ? Dark.success : Light.success }
  static func divider(_ s: ColorScheme) -> Color { s == .dark ? Color.white.opacity(0.12) : Color(hex: "1F1D1A").opacity(0.12) }

  enum Space {
    static let xs: CGFloat = 8
    static let sm: CGFloat = 12
    static let md: CGFloat = 16
    static let lg: CGFloat = 20
    static let xl: CGFloat = 24
    static let section: CGFloat = 32
    static let cardRadius: CGFloat = 20
    static let inputRadius: CGFloat = 12
    static let controlHeight: CGFloat = 48
  }

  enum Copy {
    static let empty = "No stops yet. Import a CSV or scan a label to get started."
    static let loading = "Your route, sorted."
    static let optimising = "Optimising route…"
    static let scanError = "Couldn't read the label. Try again or enter the address manually."
    static let offline = "You're offline. Stops are saved on this device; route optimisation will run when you reconnect."
    static let saved = "Stop added."
    static let unresolved = "Address not found — tap to edit."
    static let mapsBatchTitle = "Open route in Google Maps"
  }
}

extension Color {
  init(hex: String) {
    let value = Int(hex, radix: 16) ?? 0
    self.init(
      red: Double((value >> 16) & 0xFF) / 255,
      green: Double((value >> 8) & 0xFF) / 255,
      blue: Double(value & 0xFF) / 255
    )
  }
}

struct GlassBackground: View {
  var tint: Color = .clear
  var body: some View {
    ZStack {
      Color.clear.background(.ultraThinMaterial)
      tint.opacity(0.35)
    }
  }
}

struct GlassTabBar: View {
  @Environment(\.colorScheme) private var scheme
  let selection: Int
  let onSelect: (Int) -> Void
  private let items = [
    ("Route", "map"), ("Scan", "camera"), ("Map", "navigation"), ("Archive", "archivebox")
  ]

  var body: some View {
    HStack(spacing: 0) {
      ForEach(Array(items.enumerated()), id: \.offset) { index, item in
        Button { onSelect(index) } label: {
          VStack(spacing: 2) {
            Image(systemName: item.1).font(.system(size: 20, weight: selection == index ? .semibold : .regular))
            Text(item.0).font(.system(size: 12, weight: selection == index ? .semibold : .regular))
          }
          .frame(maxWidth: .infinity)
          .foregroundStyle(selection == index ? RP.accentText(scheme) : RP.secondary(scheme))
          .overlay(alignment: .top) {
            if selection == index {
              Capsule().fill(RP.accentFill(scheme)).frame(width: 26, height: 3)
            }
          }
          .frame(height: 48)
          .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.0)
      }
    }
    .padding(.vertical, 6)
    .background(GlassBackground())
    .overlay(alignment: .top) { Divider().background(RP.divider(scheme)) }
  }
}

struct PrimaryButton: View {
  @Environment(\.colorScheme) private var scheme
  let title: String
  var systemImage: String?
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        if let systemImage { Image(systemName: systemImage) }
        Text(title).font(.system(size: 17, weight: .semibold))
      }
      .frame(maxWidth: .infinity)
      .frame(height: RP.Space.controlHeight)
      .background(RP.accentFill(scheme))
      .foregroundStyle(RP.onAccent(scheme))
      .clipShape(RoundedRectangle(cornerRadius: 14))
      .contentShape(RoundedRectangle(cornerRadius: 14))
    }
    .buttonStyle(.plain)
  }
}

struct SecondaryButton: View {
  @Environment(\.colorScheme) private var scheme
  let title: String
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      Text(title)
        .font(.system(size: 16, weight: .semibold))
        .frame(maxWidth: .infinity)
        .frame(height: 44)
        .background(RP.surface(scheme))
        .foregroundStyle(RP.text(scheme))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(RP.divider(scheme), lineWidth: 1))
        .contentShape(RoundedRectangle(cornerRadius: 14))
    }
    .buttonStyle(.plain)
  }
}

struct StatusPill: View {
  @Environment(\.colorScheme) private var scheme
  let status: StopStatus

  var body: some View {
    HStack(spacing: 6) {
      Image(systemName: status == .done ? "checkmark.circle.fill" : status == .failed ? "xmark.circle.fill" : "circle")
      Text(status.rawValue.capitalized)
    }
    .font(.system(size: 14, weight: .semibold))
    .padding(.horizontal, 12)
    .padding(.vertical, 7)
    .background(RP.surfaceMuted(scheme))
    .foregroundStyle(status == .done ? RP.success(scheme) : status == .failed ? RP.error(scheme) : RP.text(scheme))
    .clipShape(Capsule())
    .accessibilityLabel(status.rawValue.capitalized)
  }
}

struct FilterChip: View {
  @Environment(\.colorScheme) private var scheme
  let title: String
  let symbol: String?
  let selected: Bool
  let action: () -> Void

  var body: some View {
    Button(action: action) {
      HStack(spacing: 5) {
        if let symbol { Image(systemName: symbol) }
        Text(title).font(.system(size: 16, weight: .semibold))
      }
      .padding(.horizontal, 16)
      .padding(.vertical, 10)
      .background(selected ? RP.charcoal(scheme) : RP.surfaceMuted(scheme))
      .foregroundStyle(selected ? RP.canvas(scheme) : RP.text(scheme))
      .clipShape(Capsule())
    }
    .buttonStyle(.plain)
    .accessibilityLabel(title)
  }
}
