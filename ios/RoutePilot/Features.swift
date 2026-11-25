import SwiftUI
import SwiftData
import MapKit
import PhotosUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Location

final class LocationProvider: NSObject, CLLocationManagerDelegate {
  static let shared = LocationProvider()
  private let manager = CLLocationManager()
  private var last: CLLocation?

  override init() {
    super.init()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyBest
  }

  func request() {
    manager.requestWhenInUseAuthorization()
    manager.startUpdatingLocation()
  }

  func current() -> (Double, Double)? {
    if let last { return (last.coordinate.latitude, last.coordinate.longitude) }
    return manager.location.map { ($0.coordinate.latitude, $0.coordinate.longitude) }
  }

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    last = locations.last
  }
}

// MARK: - Loading

struct LoadingScreen: View {
  @Environment(\.colorScheme) private var scheme
  @State private var trim: CGFloat = 0

  var body: some View {
    ZStack {
      RP.charcoal(scheme).ignoresSafeArea()
      VStack(spacing: 8) {
        Path { path in
          path.move(to: CGPoint(x: 24, y: 104))
          path.addCurve(
            to: CGPoint(x: 166, y: 22),
            control1: CGPoint(x: 78, y: 104),
            control2: CGPoint(x: 104, y: 22)
          )
        }
        .trim(from: 0, to: trim)
        .stroke(RP.accentFill(scheme), style: StrokeStyle(lineWidth: 4, lineCap: .round))
        .frame(width: 190, height: 120)
        Circle().fill(RP.accentFill(scheme)).frame(width: 16, height: 16)
          .offset(x: -71, y: 48)
        Circle().fill(RP.accentFill(scheme)).frame(width: 16, height: 16)
          .offset(x: 71, y: -42)
        Text("RoutePilot")
          .font(.system(size: 26, weight: .bold, design: .rounded))
          .foregroundStyle(RP.canvas(scheme))
          .padding(.top, 24)
        Text(RP.Copy.loading)
          .font(.system(size: 16))
          .foregroundStyle(RP.secondary(scheme))
      }
    }
    .onAppear {
      withAnimation(.easeOut(duration: 0.8)) { trim = 1 }
    }
    .accessibilityElement(children: .combine)
    .accessibilityLabel("RoutePilot. \(RP.Copy.loading)")
  }
}

// MARK: - Root

struct ContentView: View {
  @Environment(\.modelContext) private var context
  @Environment(\.colorScheme) private var scheme
  @Query private var allRoutes: [RouteRecord]
  @Query private var allStops: [StopRecord]

  @State private var tab = 0
  @State private var ready = false
  @State private var message: String?
  @State private var showScan = false
  @State private var showAdd = false
  @State private var showOptions = false
  @State private var showSummary = false
  @State private var showSettings = false
  @State private var showReset = false
  @State private var showConsent = false
  @State private var detailStop: StopRecord?
  @State private var importCSV = false
  @State private var backupData: Data?
  @State private var showExportBackup = false
  @State private var showImportBackup = false
  @State private var busy = false

  private var activeRoute: RouteRecord? { allRoutes.first { $0.active } }
  private var routeStops: [StopRecord] {
    guard let route = activeRoute else { return [] }
    return allStops.filter { $0.route?.id == route.id }
  }
  private var stops: [StopRecord] { routeStops.sorted { $0.orderIndex < $1.orderIndex } }

  var body: some View {
    Group {
      if ready {
        mainView
      } else {
        LoadingScreen()
      }
    }
    .task {
      let start = Date()
      LocationProvider.shared.request()
      ensureRoute()
      Task { await NotificationScheduler.requestPermission() }
      let elapsed = Date().timeIntervalSince(start)
      if elapsed < 0.8 {
        try? await Task.sleep(nanoseconds: UInt64((0.8 - elapsed) * 1_000_000_000))
      }
      ready = true
    }
    .sheet(isPresented: $showScan) {
      ScanView(
        onSave: { address, label, recipient, phone, postcode, windowStart, windowEnd in
          addStop(
            address: address, label: label, recipient: recipient, phone: phone,
            postcode: postcode, windowStart: windowStart, windowEnd: windowEnd
          )
          showScan = false
        },
        onMessage: { message = $0 },
        onConsent: { showConsent = true }
      )
    }
    .sheet(isPresented: $showAdd) {
      AddStopView { address, label, recipient, phone, windowStart, windowEnd in
        addStop(
          address: address, label: label, recipient: recipient, phone: phone,
          postcode: "", windowStart: windowStart, windowEnd: windowEnd
        )
        showAdd = false
      }
    }
    .sheet(isPresented: $showOptions) {
      OptionsSheet(
        onSummary: { showSummary = true },
        onBackup: { backupData = makeBackup(); showExportBackup = true },
        onRestore: { showImportBackup = true },
        onSettings: { showSettings = true },
        onMaps: { openMaps() },
        onReset: { showReset = true },
        onClose: { showOptions = false }
      )
    }
    .sheet(isPresented: $showSummary) { SummaryView(stops: stops, onClose: { showSummary = false }) }
    .sheet(isPresented: $showSettings) { AiSettingsView() }
    .sheet(isPresented: Binding(get: { detailStop != nil }, set: { if !$0 { detailStop = nil } })) {
      if let stop = detailStop {
        StopDetailSheet(
          stop: stop,
          onNavigate: { tab = 2; detailStop = nil },
          onReorder: { direction in move(stop, by: direction) },
          onClose: { detailStop = nil }
        )
      }
    }
    .sheet(isPresented: $showConsent) {
      ConsentSheet(onAccept: { showConsent = false; showScan = true }, onCancel: { showConsent = false })
    }
    .fileImporter(isPresented: $importCSV, allowedContentTypes: [.plainText, .commaSeparatedText]) { result in
      if let url = try? result.get(), let text = try? String(contentsOf: url) { importStops(text) }
    }
    .fileImporter(isPresented: $showImportBackup, allowedContentTypes: [.json]) { result in
      if let url = try? result.get(), let data = try? Data(contentsOf: url) { importBackup(data) }
    }
    .fileExporter(
      isPresented: $showExportBackup,
      document: BackupDocument(data: backupData ?? Data()),
      contentType: .json,
      defaultFilename: "routepilot-backup.json"
    ) { _ in }
    .alert("RoutePilot", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
      Button("OK", role: .cancel) { message = nil }
    } message: { Text(message ?? "") }
    .alert("Reset route?", isPresented: $showReset) {
      Button("Cancel", role: .cancel) {}
      Button("Reset route", role: .destructive) { resetRoute() }
    } message: {
      Text("Pending \(stops.filter { $0.status == .pending }.count) · Done \(stops.filter { $0.status == .done }.count) · Failed \(stops.filter { $0.status == .failed }.count). The route will be archived and a new empty route started.")
    }
  }

  private var mainView: some View {
    ZStack(alignment: .bottom) {
      RP.canvas(scheme).ignoresSafeArea()
      Group {
        switch tab {
        case 0: routeTab
        case 1: scanTab
        case 2: MapScreen(stops: stops.filter { $0.status == .pending })
        default: archiveTab
        }
      }
      .padding(.bottom, 76)
      VStack {
        Spacer()
        GlassTabBar(selection: tab) { tab = $0 }
      }
    }
    .ignoresSafeArea(.keyboard)
  }

  // MARK: Route tab

  private var routeTab: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: RP.Space.lg) {
        HStack {
          Text("RoutePilot").font(.system(size: 34, weight: .bold, design: .rounded))
          Spacer()
          Button { showOptions = true } label: {
            Image(systemName: "ellipsis.circle").font(.system(size: 22))
              .frame(width: 48, height: 48)
              .background(RP.surfaceMuted(scheme))
              .foregroundStyle(RP.text(scheme))
              .clipShape(Circle())
          }
          .buttonStyle(.plain)
          .accessibilityLabel("Route options")
        }

        VStack(alignment: .leading, spacing: 8) {
          Text("Today's progress").font(.system(size: 16, weight: .semibold))
          Text("\(stops.filter { $0.status == .done }.count) of \(stops.count) stops")
            .font(.system(size: 44, weight: .bold, design: .rounded))
            .monospacedDigit()
          if busy { Text(RP.Copy.optimising).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme)) }
          if let next = stops.first(where: { $0.status == .pending }) {
            Text("Next: \(next.label.isEmpty ? next.address : next.label)")
              .font(.system(size: 17, weight: .semibold))
            Text(next.address).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
          }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(RP.Space.xl)
        .background(RP.surfaceMuted(scheme))
        .clipShape(RoundedRectangle(cornerRadius: RP.Space.cardRadius))

        HStack(spacing: 10) {
          PrimaryButton(title: "Scan label", systemImage: "camera") {
            if KeychainStore.read(key: "lucif_api_key").isEmpty { showConsent = true }
            else { showScan = true }
          }
          PrimaryButton(title: "Navigate all", systemImage: "navigation") {
            Task { await optimize(); tab = 2 }
          }
        }

        HStack(spacing: 8) {
          SecondaryButton(title: "Import CSV") { importCSV = true }
          SecondaryButton(title: "Add stop") { showAdd = true }
          SecondaryButton(title: "Optimize now") { Task { await optimize() } }
        }

        HStack(spacing: 8) {
          FilterChip(title: "All", symbol: nil, selected: filter == .all) { filter = .all }
          FilterChip(title: "Pending", symbol: nil, selected: filter == .pending) { filter = .pending }
          FilterChip(title: "Done", symbol: nil, selected: filter == .done) { filter = .done }
          FilterChip(title: "Failed", symbol: "exclamationmark", selected: filter == .failed) { filter = .failed }
        }

        let visible = stops.filter { stop in
          switch filter {
          case .all: return true
          case .pending: return stop.status == .pending
          case .done: return stop.status == .done
          case .failed: return stop.status == .failed
          }
        }

        if visible.isEmpty {
          VStack(alignment: .leading, spacing: 8) {
            Text("Nothing here.").font(.system(size: 18, weight: .semibold))
            Text(RP.Copy.empty).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
          }
          .frame(maxWidth: .infinity, alignment: .leading)
          .padding(RP.Space.xl)
          .background(RP.surface(scheme))
          .clipShape(RoundedRectangle(cornerRadius: RP.Space.cardRadius))
        } else {
          List {
            ForEach(Array(visible.enumerated()), id: \.element.id) { _, stop in
              stopCard(stop)
                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }
            .onMove { indices, to in
              guard let from = indices.first else { return }
              let moving = visible
              guard from < moving.count, to < moving.count else { return }
              var reordered = moving
              let item = reordered.remove(at: from)
              reordered.insert(item, at: to)
              for (index, stop) in reordered.enumerated() { stop.orderIndex = index }
            }
          }
          .listStyle(.plain)
          .scrollDisabled(true)
          .frame(height: CGFloat(visible.count) * 132)
        }
      }
      .padding(.horizontal, RP.Space.lg)
      .padding(.top, RP.Space.md)
      .padding(.bottom, RP.Space.xl)
    }
  }

  @State private var filter: StopFilter = .all

  private func stopCard(_ stop: StopRecord) -> some View {
    VStack(alignment: .leading, spacing: 10) {
      HStack(alignment: .top, spacing: 12) {
        Text("\(stop.orderIndex + 1)")
          .font(.system(size: 17, weight: .bold, design: .rounded))
          .monospacedDigit()
          .foregroundStyle(RP.accentText(scheme))
        VStack(alignment: .leading, spacing: 4) {
          Text(stop.label.isEmpty ? stop.address : stop.label)
            .font(.system(size: 17, weight: .semibold))
          Text(stop.address).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
        }
        Spacer()
      }
      HStack {
        StatusPill(status: stop.status)
        Spacer()
        if stop.status == .pending {
          Button("Done") { complete(stop) }
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(RP.accentText(scheme))
        }
      }
      if stop.estimatedCoordinates {
        Text(RP.Copy.estimated).font(.system(size: 12)).foregroundStyle(RP.error(scheme))
      }
    }
    .padding(RP.Space.lg)
    .background(RP.surface(scheme))
    .clipShape(RoundedRectangle(cornerRadius: RP.Space.cardRadius))
    .contentShape(Rectangle())
    .onTapGesture { detailStop = stop }
    .accessibilityElement(children: .combine)
    .accessibilityLabel("Stop \(stop.orderIndex + 1), \(stop.label.isEmpty ? stop.address : stop.label), \(stop.status.rawValue)")
  }

  private var scanTab: some View {
    VStack(spacing: RP.Space.lg) {
      Spacer()
      Image(systemName: "camera.viewfinder")
        .font(.system(size: 64))
        .foregroundStyle(RP.accentText(scheme))
      Text("Scan the next label").font(.system(size: 22, weight: .semibold, design: .rounded))
      Text("AI reads the address; you confirm it before saving.")
        .font(.system(size: 17)).foregroundStyle(RP.secondary(scheme))
        .multilineTextAlignment(.center)
      PrimaryButton(title: "Open scanner", systemImage: "camera") {
        if KeychainStore.read(key: "lucif_api_key").isEmpty { showConsent = true }
        else { showScan = true }
      }
      Spacer()
    }
    .padding(.horizontal, RP.Space.lg)
  }

  private var archiveTab: some View {
    NavigationStack {
      List {
        if allRoutes.filter({ !$0.active }).isEmpty {
          Text("No archived routes.").foregroundStyle(RP.secondary(scheme))
        }
        ForEach(allRoutes.filter { !$0.active }) { route in
          VStack(alignment: .leading, spacing: 4) {
            Text(route.name).font(.system(size: 17, weight: .semibold))
            Text(route.date).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
          }
          .listRowBackground(RP.surface(scheme))
        }
      }
      .listStyle(.insetGrouped)
      .scrollContentBackground(.hidden)
      .background(RP.canvas(scheme))
      .navigationTitle("Archive")
    }
  }

  // MARK: Actions

  private func ensureRoute() {
    guard activeRoute == nil else { return }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    context.insert(RouteRecord(name: "route-\(formatter.string(from: Date()))", date: formatter.string(from: Date()), active: true))
  }

  private func addStop(address: String, label: String, recipient: String, phone: String,
                       postcode: String, windowStart: String, windowEnd: String) {
    guard !address.trimmingCharacters(in: .whitespaces).isEmpty else {
      message = "Address is required"
      return
    }
    let fullAddress = postcode.isEmpty ? address : "\(address), \(postcode)"
    let stop = StopRecord(
      route: activeRoute,
      address: fullAddress,
      label: label,
      recipient: recipient,
      phone: phone,
      windowStart: windowStart,
      windowEnd: windowEnd,
      orderIndex: stops.count
    )
    context.insert(stop)
    NotificationScheduler.schedule(label: label.isEmpty ? fullAddress : label, windowStart: windowStart, stopID: stop.id)
    message = RP.Copy.saved
    Task { await optimize() }
  }

  private func importStops(_ text: String) {
    let rows = CsvParser.parse(text)
    var count = 0
    for row in rows {
      guard let address = row["address"], !address.isEmpty else { continue }
      let windowStart = row["window_start"] ?? row["windowstart"] ?? ""
      let windowEnd = row["window_end"] ?? row["windowend"] ?? ""
      let stop = StopRecord(
        route: activeRoute,
        address: address,
        label: row["label"] ?? "",
        recipient: row["recipient"] ?? "",
        phone: row["phone"] ?? "",
        notes: row["notes"] ?? "",
        windowStart: windowStart,
        windowEnd: windowEnd,
        latitude: Double(row["latitude"] ?? ""),
        longitude: Double(row["longitude"] ?? ""),
        orderIndex: stops.count + count
      )
      context.insert(stop)
      if !windowStart.isEmpty {
        NotificationScheduler.schedule(label: stop.label.isEmpty ? address : stop.label, windowStart: windowStart, stopID: stop.id)
      }
      count += 1
    }
    message = "Imported \(count) stops"
    Task { await optimize() }
  }

  private func complete(_ stop: StopRecord) {
    stop.status = .done
    NotificationScheduler.cancel(stopID: stop.id)
    Task { await optimize() }
  }

  private func fail(_ stop: StopRecord) {
    stop.status = .failed
    stop.failureReason = .notHome
    NotificationScheduler.cancel(stopID: stop.id)
    Task { await optimize() }
  }

  private func move(_ stop: StopRecord, by direction: Int) {
    guard let index = stops.firstIndex(where: { $0.id == stop.id }) else { return }
    let target = index + direction
    guard target >= 0, target < stops.count else { return }
    let ordered = stops
    let moved = ordered[index]
    var reordered = ordered
    reordered.remove(at: index)
    reordered.insert(moved, at: target)
    for (i, item) in reordered.enumerated() { item.orderIndex = i }
  }

  private func optimize() async {
    await MainActor.run { busy = true }
    let origin = LocationProvider.shared.current()
    let pending = stops.filter { $0.status == .pending }
    var coords: [(Double, Double)] = []
    for stop in pending {
      if stop.latitude == nil || stop.longitude == nil {
        if let point = await Nominatim.geocode(stop.address) {
          stop.latitude = point.0
          stop.longitude = point.1
        } else if let point = try? await LucifClient.coordinates(for: stop.address) {
          stop.latitude = point.0
          stop.longitude = point.1
          stop.estimatedCoordinates = true
        }
      }
      if let lat = stop.latitude, let lon = stop.longitude { coords.append((lat, lon)) }
    }
    guard let start = origin ?? coords.first, coords.count >= 2,
          let matrix = await Osrm.table(origin: start, stops: coords) else {
      await MainActor.run {
        busy = false
        message = "Road routing unavailable — using straight lines"
      }
      return
    }
    let order = RouteSolver.order(matrix: matrix, stopCount: coords.count)
    for (index, candidate) in order.enumerated() where pending.indices.contains(candidate) {
      pending[candidate].orderIndex = index
    }
    await MainActor.run {
      busy = false
      message = "Route optimized"
    }
  }

  private func openMaps() {
    let pending = stops.filter { $0.status == .pending }
    guard !pending.isEmpty else { message = "No pending stops"; return }
    let chunks = stride(from: 0, to: pending.count, by: 9).map { offset in
      Array(pending[offset..<Swift.min(offset + 9, pending.count)])
    }
    guard let first = chunks.first, var components = URLComponents(string: "https://www.google.com/maps/dir/") else { return }
    let origin = LocationProvider.shared.current()
    let place: (StopRecord) -> String = { stop in
      if let lat = stop.latitude, let lon = stop.longitude { return "\(lat),\(lon)" }
      return stop.address
    }
    var query = [URLQueryItem(name: "api", value: "1")]
    if let origin { query.append(URLQueryItem(name: "origin", value: "\(origin.0),\(origin.1)")) }
    query.append(URLQueryItem(name: "destination", value: place(first[first.count - 1])))
    if first.count > 1 {
      query.append(URLQueryItem(name: "waypoints", value: first.dropLast().map(place).joined(separator: "|")))
    }
    query.append(URLQueryItem(name: "travelmode", value: "driving"))
    query.append(URLQueryItem(name: "dir_action", value: "navigate"))
    components.queryItems = query
    if let url = components.url { UIApplication.shared.open(url) }
  }

  private func resetRoute() {
    activeRoute?.active = false
    ensureRoute()
    message = "Route archived. New route started."
  }

  private func makeBackup() -> Data {
    let routes = allRoutes.map { RouteDTO(name: $0.name, date: $0.date, active: $0.active) }
    let stops = allStops.map { stop in
      StopDTO(
        id: stop.id,
        address: stop.address, label: stop.label, recipient: stop.recipient, phone: stop.phone,
        notes: stop.notes, priority: stop.priority, windowStart: stop.windowStart, windowEnd: stop.windowEnd,
        latitude: stop.latitude, longitude: stop.longitude, orderIndex: stop.orderIndex, pinned: stop.pinned,
        status: stop.status, failureReason: stop.failureReason, failureNote: stop.failureNote,
        retryAt: stop.retryAt, trackingId: stop.trackingId, estimatedCoordinates: stop.estimatedCoordinates
      )
    }
    return (try? JSONEncoder().encode(BackupPayload(routes: routes, stops: stops))) ?? Data()
  }

  private func importBackup(_ data: Data) {
    guard let payload = try? JSONDecoder().decode(BackupPayload.self, from: data) else {
      message = "Restore failed"
      return
    }
    for route in payload.routes { context.insert(RouteRecord(name: route.name, date: route.date, active: route.active)) }
    for stop in payload.stops {
      context.insert(StopRecord(
        id: stop.id.isEmpty ? UUID().uuidString : stop.id,
        address: stop.address, label: stop.label, recipient: stop.recipient, phone: stop.phone,
        notes: stop.notes, priority: stop.priority, windowStart: stop.windowStart, windowEnd: stop.windowEnd,
        latitude: stop.latitude, longitude: stop.longitude, orderIndex: stop.orderIndex, pinned: stop.pinned,
        status: stop.status, failureReason: stop.failureReason, failureNote: stop.failureNote,
        retryAt: stop.retryAt, trackingId: stop.trackingId, estimatedCoordinates: stop.estimatedCoordinates
      ))
    }
    message = "Backup restored"
  }
}

enum StopFilter { case all, pending, done, failed }

// MARK: - Options and summary

struct OptionsSheet: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.colorScheme) private var scheme
  let onSummary: () -> Void
  let onBackup: () -> Void
  let onRestore: () -> Void
  let onSettings: () -> Void
  let onMaps: () -> Void
  let onReset: () -> Void
  let onClose: () -> Void

  var body: some View {
    NavigationStack {
      List {
        row("Summary", symbol: "chart.bar") { run(onSummary) }
        row("Open in Maps", symbol: "map") { run(onMaps) }
        row("Backup", symbol: "square.and.arrow.up") { run(onBackup) }
        row("Restore", symbol: "square.and.arrow.down") { run(onRestore) }
        row("AI settings", symbol: "key") { run(onSettings) }
        row("Reset route", symbol: "arrow.counterclockwise", destructive: true) { run(onReset) }
      }
      .listStyle(.insetGrouped)
      .scrollContentBackground(.hidden)
      .background(RP.canvas(scheme))
      .navigationTitle("Route options")
      .toolbar { Button("Close") { dismiss(); onClose() } }
    }
  }

  private func run(_ action: @escaping () -> Void) {
    dismiss()
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.15, execute: action)
  }

  private func row(_ title: String, symbol: String, destructive: Bool = false, action: @escaping () -> Void) -> some View {
    Button(action: action) {
      HStack {
        Image(systemName: symbol).frame(width: 26)
        Text(title)
        Spacer()
        Image(systemName: "chevron.right").foregroundStyle(RP.secondary(scheme))
      }
      .font(.system(size: 17))
      .foregroundStyle(destructive ? RP.error(scheme) : RP.text(scheme))
      .padding(.vertical, 6)
    }
    .buttonStyle(.plain)
  }
}

struct SummaryView: View {
  @Environment(\.colorScheme) private var scheme
  let stops: [StopRecord]
  let onClose: () -> Void

  var body: some View {
    NavigationStack {
      List {
        Section("Today") {
          summaryRow("Pending", count: stops.filter { $0.status == .pending }.count)
          summaryRow("Done", count: stops.filter { $0.status == .done }.count)
          summaryRow("Failed", count: stops.filter { $0.status == .failed }.count)
        }
        Section("Stops") {
          ForEach(stops) { stop in
            VStack(alignment: .leading) {
              Text(stop.label.isEmpty ? stop.address : stop.label).font(.system(size: 16))
              Text(stop.status.rawValue).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
            }
          }
        }
      }
      .listStyle(.insetGrouped)
      .scrollContentBackground(.hidden)
      .background(RP.canvas(scheme))
      .navigationTitle("Summary")
      .toolbar { Button("Close") { onClose() } }
    }
  }

  private func summaryRow(_ title: String, count: Int) -> some View {
    HStack { Text(title); Spacer(); Text("\(count)").monospacedDigit() }
  }
}

struct BackupDocument: FileDocument {
  var data: Data
  static var readableContentTypes: [UTType] { [.json] }
  init(data: Data) { self.data = data }
  init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
  func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct ConsentSheet: View {
  @Environment(\.colorScheme) private var scheme
  let onAccept: () -> Void
  let onCancel: () -> Void
  var body: some View {
    VStack(alignment: .leading, spacing: RP.Space.lg) {
      Text("Improve label reading?").font(.system(size: 22, weight: .bold, design: .rounded))
      Text("You can send label photos to lucif.ai for AI reading. Addresses may use Nominatim and OSRM for routing. All route data stays on this device; only the label image is sent when you use AI.")
        .font(.system(size: 17))
      Text("You can use on-device reading instead, with no key and no network.")
        .font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
      PrimaryButton(title: "Set AI key", action: onAccept)
      SecondaryButton(title: "Use device only") { onCancel() }
    }
    .padding(RP.Space.xl)
    .presentationDetents([.medium])
  }
}

// MARK: - Scan

struct ScanView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.colorScheme) private var scheme
  @State private var image: UIImage?
  @State private var showPicker = false
  @State private var source: UIImagePickerController.SourceType = .camera
  @State private var name = ""
  @State private var address = ""
  @State private var postcode = ""
  @State private var phone = ""
  @State private var windowStart = ""
  @State private var windowEnd = ""
  @State private var warnings: [String] = []
  @State private var sourceLabel = ""
  @State private var showSettings = false
  @State private var busy = false
  let onSave: (String, String, String, String, String, String, String) -> Void
  let onMessage: (String) -> Void
  let onConsent: () -> Void

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: RP.Space.lg) {
          if let image {
            Image(uiImage: image).resizable().scaledToFit()
              .frame(maxHeight: 240).clipShape(RoundedRectangle(cornerRadius: 20))
            if !sourceLabel.isEmpty {
              HStack {
                Image(systemName: sourceLabel == "Read by AI" ? "sparkles" : "iphone")
                Text(sourceLabel)
              }
              .font(.system(size: 14, weight: .semibold))
              .padding(.horizontal, 12).padding(.vertical, 7)
              .background(RP.surfaceMuted(scheme)).clipShape(Capsule())
            }
            field("Recipient", $name)
            field("Address", $address)
            field("Postcode", $postcode)
            field("Phone", $phone)
            field("Window start (HH:mm)", $windowStart)
            field("Window end (HH:mm)", $windowEnd)
            if !warnings.isEmpty {
              Text(warnings.joined(separator: "; "))
                .font(.system(size: 14)).foregroundStyle(RP.error(scheme))
            }
            PrimaryButton(title: "Save stop", systemImage: "checkmark") { save() }
            SecondaryButton(title: "Retake") { self.image = nil }
          } else {
            Image(systemName: "camera.viewfinder").font(.system(size: 56))
              .foregroundStyle(RP.accentText(scheme))
            Text("Point the camera at a parcel label.")
              .font(.system(size: 17)).foregroundStyle(RP.secondary(scheme))
            PrimaryButton(title: "Take photo", systemImage: "camera") {
              source = .camera; showPicker = true
            }
            SecondaryButton(title: "Choose from photo library") {
              source = .photoLibrary; showPicker = true
            }
          }
          Button("AI settings") { showSettings = true }
            .font(.system(size: 14)).foregroundStyle(RP.accentText(scheme))
          if busy { ProgressView() }
        }
        .padding(RP.Space.lg)
      }
      .navigationTitle("Scan label")
      .toolbar { Button("Close") { dismiss() } }
      .sheet(isPresented: $showPicker) {
        ImagePicker(sourceType: source) { picked in
          image = picked
          Task { await read(picked) }
        }
      }
      .sheet(isPresented: $showSettings) { AiSettingsView() }
    }
  }

  private func field(_ title: String, _ value: Binding<String>) -> some View {
    TextField(title, text: value)
      .font(.system(size: 17))
      .padding(.horizontal, 16)
      .frame(height: RP.Space.controlHeight)
      .background(RP.surface(scheme))
      .clipShape(RoundedRectangle(cornerRadius: RP.Space.inputRadius))
      .overlay(RoundedRectangle(cornerRadius: RP.Space.inputRadius).stroke(RP.divider(scheme), lineWidth: 1))
  }

  private func read(_ image: UIImage) async {
    busy = true
    if let result = try? await LucifClient.fields(from: image) {
      apply(result)
      sourceLabel = "Read by AI"
    } else {
      let result = await DeviceOCR.fields(from: image)
      apply(result)
      sourceLabel = "Read on device"
      if result.warnings.isEmpty { warnings = ["Read on device — check the details"] }
    }
    busy = false
  }

  private func apply(_ result: LabelParser.Result) {
    name = result.name
    address = result.address
    postcode = result.postcode
    phone = result.phone
    warnings = result.warnings
  }

  private func save() {
    guard !address.trimmingCharacters(in: .whitespaces).isEmpty else {
      onMessage("Address is required")
      return
    }
    onSave(address, name, name, phone, postcode, windowStart, windowEnd)
    dismiss()
  }
}

struct AiSettingsView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.colorScheme) private var scheme
  @State private var key = ""
  var body: some View {
    NavigationStack {
      Form {
        SecureField("lucif.ai API key", text: $key)
        Text("Stored in the iOS Keychain. Sending a label uses lucif.ai; addresses may use Nominatim and OSRM.")
          .font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
        Button("Save") { KeychainStore.save(key, key: "lucif_api_key"); dismiss() }
      }
      .scrollContentBackground(.hidden)
      .background(RP.canvas(scheme))
      .navigationTitle("AI settings")
      .toolbar { Button("Cancel") { dismiss() } }
      .onAppear { key = KeychainStore.read(key: "lucif_api_key") }
    }
  }
}

struct ImagePicker: UIViewControllerRepresentable {
  let sourceType: UIImagePickerController.SourceType
  let completion: (UIImage) -> Void

  func makeUIViewController(context: Context) -> UIImagePickerController {
    let picker = UIImagePickerController()
    picker.sourceType = sourceType
    picker.delegate = context.coordinator
    return picker
  }

  func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

  func makeCoordinator() -> Coordinator { Coordinator(completion: completion) }

  final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    let completion: (UIImage) -> Void
    init(completion: @escaping (UIImage) -> Void) { self.completion = completion }
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
      if let chosen = info[.originalImage] as? UIImage { completion(chosen) }
      picker.dismiss(animated: true)
    }
  }
}

// MARK: - Add and detail

struct AddStopView: View {
  @Environment(\.dismiss) private var dismiss
  @Environment(\.colorScheme) private var scheme
  @State private var address = ""
  @State private var label = ""
  @State private var recipient = ""
  @State private var phone = ""
  @State private var windowStart = ""
  @State private var windowEnd = ""
  let save: (String, String, String, String, String, String) -> Void

  var body: some View {
    NavigationStack {
      Form {
        TextField("Address", text: $address)
        TextField("Label", text: $label)
        TextField("Recipient", text: $recipient)
        TextField("Phone", text: $phone)
        TextField("Window start (HH:mm)", text: $windowStart)
        TextField("Window end (HH:mm)", text: $windowEnd)
        Button("Add stop") { save(address, label, recipient, phone, windowStart, windowEnd) }
      }
      .scrollContentBackground(.hidden)
      .background(RP.canvas(scheme))
      .navigationTitle("Add stop")
      .toolbar { Button("Cancel") { dismiss() } }
    }
  }
}

struct StopDetailSheet: View {
  @Environment(\.colorScheme) private var scheme
  @Environment(\.modelContext) private var context
  let stop: StopRecord
  let onNavigate: () -> Void
  let onReorder: (Int) -> Void
  let onClose: () -> Void

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: RP.Space.lg) {
          VStack(alignment: .leading, spacing: 4) {
            Text(stop.label.isEmpty ? stop.address : stop.label)
              .font(.system(size: 22, weight: .bold, design: .rounded))
            Text(stop.address).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
            StatusPill(status: stop.status)
          }
          .frame(maxWidth: .infinity, alignment: .leading)

          VStack(spacing: 8) {
            detailField("Recipient", Binding(get: { stop.recipient }, set: { stop.recipient = $0 }))
            detailField("Phone", Binding(get: { stop.phone }, set: { stop.phone = $0 }))
            detailField("Address", Binding(get: { stop.address }, set: { stop.address = $0 }))
            detailField("Window start (HH:mm)", Binding(get: { stop.windowStart }, set: { stop.windowStart = $0 }))
            detailField("Window end (HH:mm)", Binding(get: { stop.windowEnd }, set: { stop.windowEnd = $0 }))
            detailField("Notes", Binding(get: { stop.notes }, set: { stop.notes = $0 }))
          }

          VStack(spacing: 8) {
            PrimaryButton(title: "Done", systemImage: "checkmark") {
              stop.status = .done
              NotificationScheduler.cancel(stopID: stop.id)
              onClose()
            }
            SecondaryButton(title: "Save changes") {
              if !stop.windowStart.isEmpty {
                NotificationScheduler.schedule(
                  label: stop.label.isEmpty ? stop.address : stop.label,
                  windowStart: stop.windowStart,
                  stopID: stop.id
                )
              }
              onClose()
            }
            SecondaryButton(title: "Failed") { stop.status = .failed; stop.failureReason = .notHome; onClose() }
            SecondaryButton(title: "Navigate") { onNavigate() }
            SecondaryButton(title: stop.pinned ? "Unpin stop" : "Pin stop") { stop.pinned.toggle() }
          }

          HStack(spacing: 8) {
            SecondaryButton(title: "Move up") { onReorder(-1) }
            SecondaryButton(title: "Move down") { onReorder(1) }
          }

          if stop.estimatedCoordinates {
            Text(RP.Copy.estimated).font(.system(size: 14)).foregroundStyle(RP.error(scheme))
          }
        }
        .padding(RP.Space.lg)
      }
      .background(RP.canvas(scheme))
      .navigationTitle("Stop details")
      .toolbar { Button("Close") { onClose() } }
    }
  }

  @ViewBuilder
  private func detailField(_ title: String, _ value: Binding<String>) -> some View {
    TextField(title, text: value)
      .font(.system(size: 17))
      .padding(.horizontal, 16)
      .frame(height: RP.Space.controlHeight)
      .background(RP.surface(scheme))
      .clipShape(RoundedRectangle(cornerRadius: RP.Space.inputRadius))
  }
}

// MARK: - Map

struct MapScreen: View {
  @Environment(\.colorScheme) private var scheme
  let stops: [StopRecord]
  @State private var route: RoutePath?
  @State private var recenter = false

  var body: some View {
    ZStack(alignment: .bottom) {
      MapViewRepresentable(stops: stops, route: route, recenter: recenter)
        .ignoresSafeArea(edges: [.top, .horizontal])
      VStack(spacing: 10) {
        HStack(spacing: 16) {
          stat("\(route.map { String(format: "%.1f", $0.distanceKm) } ?? "—")", "km")
          stat("\(route.map { String(format: "%.0f", $0.durationMin) } ?? "—")", "min")
          stat("\(stops.count)", "stops")
        }
        HStack(spacing: 8) {
          SecondaryButton(title: "Recalculate") { Task { await loadRoute() } }
          SecondaryButton(title: "Recenter") { recenter.toggle() }
        }
      }
      .padding(RP.Space.lg)
      .background(GlassBackground(tint: RP.charcoal(scheme)))
      .clipShape(RoundedRectangle(cornerRadius: RP.Space.cardRadius))
      .padding(.horizontal, RP.Space.lg)
      .padding(.bottom, 12)
    }
    .task { await loadRoute() }
  }

  private func stat(_ value: String, _ unit: String) -> some View {
    HStack(alignment: .firstTextBaseline, spacing: 3) {
      Text(value).font(.system(size: 17, weight: .bold)).monospacedDigit()
      Text(unit).font(.system(size: 14)).foregroundStyle(RP.secondary(scheme))
    }
  }

  private func loadRoute() async {
    let coords = stops.compactMap { stop -> (Double, Double)? in
      guard let lat = stop.latitude, let lon = stop.longitude else { return nil }
      return (lat, lon)
    }
    guard let origin = LocationProvider.shared.current() ?? coords.first, !coords.isEmpty else { return }
    route = await Osrm.route(origin: origin, stops: coords)
  }
}

struct MapViewRepresentable: UIViewRepresentable {
  let stops: [StopRecord]
  let route: RoutePath?
  let recenter: Bool

  func makeUIView(context: Context) -> MKMapView {
    let map = MKMapView()
    map.delegate = context.coordinator
    map.showsUserLocation = true
    return map
  }

  func updateUIView(_ map: MKMapView, context: Context) {
    map.removeAnnotations(map.annotations)
    map.removeOverlays(map.overlays)
    for (index, stop) in stops.enumerated() {
      guard let lat = stop.latitude, let lon = stop.longitude else { continue }
      let annotation = MKPointAnnotation()
      annotation.coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lon)
      annotation.title = "\(index + 1). \(stop.label.isEmpty ? stop.address : stop.label)"
      map.addAnnotation(annotation)
    }
    if let route, route.points.count >= 2 {
      let coordinates = route.points.map { CLLocationCoordinate2D(latitude: $0.0, longitude: $0.1) }
      map.addOverlay(MKPolyline(coordinates: coordinates, count: coordinates.count))
    }
    if recenter, let location = map.userLocation.location {
      let region = MKCoordinateRegion(
        center: location.coordinate,
        latitudinalMeters: 3000,
        longitudinalMeters: 3000
      )
      map.setRegion(region, animated: true)
    }
  }

  func makeCoordinator() -> Coordinator { Coordinator() }

  final class Coordinator: NSObject, MKMapViewDelegate {
    func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
      if let polyline = overlay as? MKPolyline {
        let renderer = MKPolylineRenderer(polyline: polyline)
        renderer.strokeColor = UIColor(red: 0.76, green: 0.25, blue: 0.05, alpha: 1)
        renderer.lineWidth = 5
        return renderer
      }
      return MKOverlayRenderer(overlay: overlay)
    }
  }
}
