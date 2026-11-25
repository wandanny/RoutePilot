import SwiftUI
import SwiftData
import MapKit
import PhotosUI
import UIKit

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

// MARK: - Root

struct ContentView: View {
  @Environment(\.modelContext) private var context
  @Query private var allRoutes: [RouteRecord]
  @Query private var allStops: [StopRecord]
  @State private var tab = 0
  @State private var message: String?
  @State private var showScan = false
  @State private var showAdd = false
  @State private var showReset = false
  @State private var showSummary = false
  @State private var importCSV = false
  @State private var backupData: Data?
  @State private var showExportBackup = false
  @State private var showImportBackup = false

  private var activeRoute: RouteRecord? { allRoutes.first { $0.active } }
  private var stops: [StopRecord] {
    allStops.filter { $0.route?.name == activeRoute?.name && $0.route?.date == activeRoute?.date }
      .sorted { $0.orderIndex < $1.orderIndex }
  }

  var body: some View {
    TabView(selection: $tab) {
      routeTab.tag(0).tabItem { Label("Route", systemImage: "map") }
      scanTab.tag(1).tabItem { Label("Scan", systemImage: "camera") }
      MapScreen(stops: stops.filter { $0.status == .pending }).tag(2).tabItem { Label("Map", systemImage: "navigation") }
      listTab(status: .failed, title: "Exceptions").tag(3).tabItem { Label("Exceptions", systemImage: "exclamationmark") }
      archiveTab.tag(4).tabItem { Label("Archive", systemImage: "archivebox") }
    }
    .onAppear { LocationProvider.shared.request(); ensureRoute() }
    .sheet(isPresented: $showScan) { ScanView { message = $0 } }
    .sheet(isPresented: $showAdd) { AddStopView { address, label, recipient, phone in
      addStop(address: address, label: label, recipient: recipient, phone: phone)
      showAdd = false
    } }
    .sheet(isPresented: $showSummary) { SummaryView(stops: stops, onClose: { showSummary = false }) }
    .fileImporter(isPresented: $importCSV, allowedContentTypes: [.plainText, .commaSeparatedText]) { result in
      if let url = try? result.get(), let text = try? String(contentsOf: url) { importStops(text) }
    }
    .fileImporter(isPresented: $showImportBackup, allowedContentTypes: [.json]) { result in
      if let url = try? result.get(), let data = try? Data(contentsOf: url) { importBackup(data) }
    }
    .fileExporter(isPresented: $showExportBackup, document: BackupDocument(data: backupData ?? Data()), contentType: .json, defaultFilename: "routepilot-backup.json") { _ in }
    .alert("RoutePilot", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
      Button("OK", role: .cancel) { message = nil }
    } message: { Text(message ?? "") }
  }

  // MARK: Route tab

  private var routeTab: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          HStack {
            Text("RoutePilot").font(.largeTitle.bold())
            Spacer()
          }
          HStack {
            Chip(text: activeRoute?.name ?? "No route")
            Chip(text: "\(stops.filter { $0.status == .done }.count) / \(stops.count) delivered")
          }
          VStack(alignment: .leading, spacing: 8) {
            Text("Today's progress").font(.headline)
            Text("\(stops.filter { $0.status == .done }.count) of \(stops.count) stops").font(.system(size: 34, weight: .semibold))
            if let next = stops.first(where: { $0.status == .pending }) {
              Text("Next: \(next.label.isEmpty ? next.address : next.label)")
              Text(next.address).font(.subheadline).foregroundStyle(.secondary)
            }
          }
          .padding()
          .frame(maxWidth: .infinity, alignment: .leading)
          .background(Color(.systemGray6))
          .cornerRadius(16)

          HStack {
            Button("Scan label") { showScan = true }.buttonStyle(.borderedProminent)
            Button("Navigate all stops") { Task { await optimize(); tab = 2 } }.buttonStyle(.borderedProminent)
          }
          HStack {
            Button("Import CSV") { importCSV = true }.buttonStyle(.bordered)
            Button("Add stop") { showAdd = true }.buttonStyle(.bordered)
            Button("Optimize now") { Task { await optimize() } }.buttonStyle(.bordered)
          }
          HStack {
            Button("Maps") { openMaps() }
            Button("Reset") { showReset = true }
            Button("Summary") { showSummary = true }
            Button("Backup") { backupData = makeBackup(); showExportBackup = true }
            Button("Restore") { showImportBackup = true }
          }
          .font(.footnote)

          if stops.isEmpty {
            VStack(alignment: .leading) {
              Text("No stops yet").font(.headline)
              Text("Import a CSV or scan a label to get started.")
            }
            .padding().frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemGray6)).cornerRadius(16)
          } else {
            ForEach(stops) { stop in stopRow(stop) }
          }
        }
        .padding()
      }
      .alert("Reset route?", isPresented: $showReset) {
        Button("Cancel", role: .cancel) {}
        Button("Reset route", role: .destructive) { resetRoute() }
      } message: {
        Text("Pending \(stops.filter { $0.status == .pending }.count) · Done \(stops.filter { $0.status == .done }.count) · Failed \(stops.filter { $0.status == .failed }.count). The route will be archived and a new empty route started.")
      }
    }
  }

  private func stopRow(_ stop: StopRecord) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(stop.label.isEmpty ? stop.address : stop.label).font(.headline)
      Text(stop.address).font(.subheadline)
      if stop.estimatedCoordinates {
        Text("Coordinates estimated by AI — check the address").font(.caption).foregroundStyle(.red)
      }
      HStack {
        Button("Navigate") { tab = 2 }
        Button("Done") { complete(stop) }
        Button("Failed") { fail(stop) }
        Button(stop.pinned ? "Unpin" : "Pin") { stop.pinned.toggle() }
      }
      .font(.footnote)
    }
    .padding()
    .background(Color(.systemBackground))
    .cornerRadius(16)
    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(.systemGray4), lineWidth: 1))
  }

  private func listTab(status: StopStatus, title: String) -> some View {
    NavigationStack {
      List {
        if allStops.filter({ $0.status == status }).isEmpty {
          Text("Nothing here.").foregroundStyle(.secondary)
        } else {
          ForEach(allStops.filter { $0.status == status }) { stop in
            VStack(alignment: .leading) {
              Text(stop.label.isEmpty ? stop.address : stop.label)
              Text(stop.address).font(.caption).foregroundStyle(.secondary)
              if status == .failed {
                Text("Reason: \(stop.failureReason?.rawValue ?? "Unknown")").font(.caption)
              }
            }
          }
        }
      }
      .navigationTitle(title)
    }
  }

  private var archiveTab: some View {
    NavigationStack {
      List {
        ForEach(allRoutes.filter { !$0.active }) { route in
          VStack(alignment: .leading) {
            Text(route.name)
            Text(route.date).font(.caption).foregroundStyle(.secondary)
          }
        }
      }
      .navigationTitle("Archive")
    }
  }

  private var scanTab: some View {
    VStack(spacing: 20) {
      Text("Scan the next parcel label").font(.title2.bold())
      Text("AI reads the address; you confirm it before saving.").foregroundStyle(.secondary)
      Button("Open scanner") { showScan = true }.buttonStyle(.borderedProminent)
    }
  }

  // MARK: Actions

  private func ensureRoute() {
    guard activeRoute == nil else { return }
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    context.insert(RouteRecord(name: "Today's route", date: formatter.string(from: Date()), active: true))
  }

  private func addStop(address: String, label: String, recipient: String, phone: String) {
    guard !address.isEmpty else { message = "Address is required"; return }
    let stop = StopRecord(address: address, label: label, recipient: recipient, phone: phone, route: activeRoute)
    context.insert(stop)
    Task { await optimize() }
  }

  private func importStops(_ text: String) {
    let rows = CsvParser.parse(text)
    var count = 0
    for row in rows {
      guard let address = row["address"], !address.isEmpty else { continue }
      context.insert(StopRecord(
        address: address,
        label: row["label"] ?? "",
        recipient: row["recipient"] ?? "",
        phone: row["phone"] ?? "",
        notes: row["notes"] ?? "",
        latitude: Double(row["latitude"] ?? ""),
        longitude: Double(row["longitude"] ?? ""),
        route: activeRoute,
      ))
      count += 1
    }
    message = "Imported \(count) stops"
    Task { await optimize() }
  }

  private func complete(_ stop: StopRecord) {
    stop.status = .done
    Task { await optimize() }
  }

  private func fail(_ stop: StopRecord) {
    stop.status = .failed
    stop.failureReason = .notHome
    Task { await optimize() }
  }

  private func optimize() async {
    let origin = LocationProvider.shared.current()
    var coords: [(Double, Double)] = []
    for stop in stops where stop.status == .pending {
      if stop.latitude == nil || stop.longitude == nil {
        if let point = await Nominatim.geocode(stop.address) {
          stop.latitude = point.0; stop.longitude = point.1
        } else if let point = try? await LucifClient.coordinates(for: stop.address) {
          stop.latitude = point.0; stop.longitude = point.1; stop.estimatedCoordinates = true
        }
      }
      if let lat = stop.latitude, let lon = stop.longitude { coords.append((lat, lon)) }
    }
    guard let start = origin ?? coords.first, coords.count >= 2,
          let matrix = await Osrm.table(origin: start, stops: coords) else {
      message = "Road routing unavailable — using straight lines"
      return
    }
    let order = RouteSolver.order(matrix: matrix, stopCount: coords.count)
    let orderedStops = stops.filter { $0.status == .pending }
    for (index, candidate) in order.enumerated() where orderedStops.indices.contains(candidate) {
      orderedStops[candidate].orderIndex = index
    }
    message = "Route optimized"
  }

  private func resetRoute() {
    activeRoute?.active = false
    ensureRoute()
    message = "Route archived. New route started."
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
    query.append(URLQueryItem(name: "destination", value: place(first.last!)))
    if first.count > 1 {
      query.append(URLQueryItem(name: "waypoints", value: first.dropLast().map(place).joined(separator: "|")))
    }
    query.append(URLQueryItem(name: "travelmode", value: "driving"))
    query.append(URLQueryItem(name: "dir_action", value: "navigate"))
    components.queryItems = query
    if let url = components.url { UIApplication.shared.open(url) }
  }

  private func makeBackup() -> Data {
    let routes = allRoutes.map { RouteDTO(name: $0.name, date: $0.date, active: $0.active) }
    let stops = allStops.map { stop in
      StopDTO(
        address: stop.address, label: stop.label, recipient: stop.recipient, phone: stop.phone,
        notes: stop.notes, priority: stop.priority, windowStart: stop.windowStart, windowEnd: stop.windowEnd,
        latitude: stop.latitude, longitude: stop.longitude, orderIndex: stop.orderIndex, pinned: stop.pinned,
        status: stop.status, failureReason: stop.failureReason, failureNote: stop.failureNote, retryAt: stop.retryAt,
        trackingId: stop.trackingId, estimatedCoordinates: stop.estimatedCoordinates,
      )
    }
    return try! JSONEncoder().encode(BackupPayload(routes: routes, stops: stops))
  }

  private func importBackup(_ data: Data) {
    guard let payload = try? JSONDecoder().decode(BackupPayload.self, from: data) else { message = "Restore failed"; return }
    for route in payload.routes { context.insert(RouteRecord(name: route.name, date: route.date, active: route.active)) }
    message = "Backup restored"
  }
}

struct Chip: View {
  var text: String
  var body: some View {
    Text(text).font(.footnote).padding(.horizontal, 12).padding(.vertical, 6)
      .background(Color(.systemGray5)).cornerRadius(14)
  }
}

// MARK: - Supporting views

struct SummaryView: View {
  let stops: [StopRecord]
  let onClose: () -> Void
  var body: some View {
    NavigationStack {
      List {
        Section("Today") {
          Text("Pending \(stops.filter { $0.status == .pending }.count)")
          Text("Done \(stops.filter { $0.status == .done }.count)")
          Text("Failed \(stops.filter { $0.status == .failed }.count)")
        }
        Section("Stops") {
          ForEach(stops) { stop in
            VStack(alignment: .leading) {
              Text(stop.label.isEmpty ? stop.address : stop.label)
              Text(stop.status.rawValue).font(.caption).foregroundStyle(.secondary)
            }
          }
        }
      }
      .navigationTitle("Summary")
      .toolbar { Button("Close") { onClose() } }
    }
  }
}

struct BackupDocument: FileDocument {
  var data: Data
  static var readableContentTypes: [UTType] { [.json] }
  init(data: Data) { self.data = data }
  init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
  func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

// MARK: - Camera and confirm

struct ScanView: View {
  @Environment(\.modelContext) private var context
  @Environment(\.dismiss) private var dismiss
  @State private var image: UIImage?
  @State private var showPicker = false
  @State private var source: UIImagePickerController.SourceType = .camera
  @State private var name = ""
  @State private var address = ""
  @State private var postcode = ""
  @State private var warnings: [String] = []
  @State private var showSettings = false
  @State private var busy = false
  let onMessage: (String) -> Void

  var body: some View {
    NavigationStack {
      VStack(spacing: 16) {
        if let image {
          Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 260).cornerRadius(12)
          TextField("Recipient", text: $name).textFieldStyle(.roundedBorder)
          TextField("Address", text: $address).textFieldStyle(.roundedBorder)
          TextField("Postcode", text: $postcode).textFieldStyle(.roundedBorder)
          if !warnings.isEmpty { Text(warnings.joined(separator: "; ")).font(.caption).foregroundStyle(.red) }
          Button("Save stop") { save() }.buttonStyle(.borderedProminent)
          Button("Retake") { self.image = nil }.buttonStyle(.bordered)
        } else {
          Text("Point the camera at a parcel label.").foregroundStyle(.secondary)
          Button("Take photo") { source = .camera; showPicker = true }.buttonStyle(.borderedProminent)
          Button("Choose from gallery") { source = .photoLibrary; showPicker = true }.buttonStyle(.bordered)
        }
        Button("AI settings") { showSettings = true }.font(.footnote)
        if busy { ProgressView() }
      }
      .padding()
      .navigationTitle("Scan label")
      .toolbar { Button("Close") { dismiss() } }
      .sheet(isPresented: $showPicker) { ImagePicker(sourceType: source) { picked in
        image = picked
        Task { await read(picked) }
      } }
      .sheet(isPresented: $showSettings) { AiSettingsView() }
    }
  }

  private func read(_ image: UIImage) async {
    busy = true
    let result = try? await LucifClient.fields(from: image)
    if let result {
      name = result.name; address = result.address; postcode = result.postcode; warnings = result.warnings
    } else {
      warnings = ["AI unavailable — enter the address manually"]
    }
    busy = false
  }

  private func save() {
    guard !address.isEmpty else { onMessage("Address is required"); return }
    let stop = StopRecord(
      address: postcode.isEmpty ? address : "\(address), \(postcode)",
      label: name,
      recipient: name,
    )
    context.insert(stop)
    onMessage("Stop added")
    dismiss()
  }
}

struct AiSettingsView: View {
  @Environment(\.dismiss) private var dismiss
  @State private var key = ""
  var body: some View {
    NavigationStack {
      Form {
        SecureField("lucif.ai API key", text: $key)
        Text("Stored in the iOS Keychain. Sending a label uses lucif.ai; addresses may use Nominatim and OSRM.")
          .font(.caption).foregroundStyle(.secondary)
        Button("Save") { KeychainStore.save(key, key: "lucif_api_key"); dismiss() }
      }
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
      if let image = info[.originalImage] as? UIImage { completion(image) }
      picker.dismiss(animated: true)
    }
  }
}

struct AddStopView: View {
  @Environment(\.dismiss) private var dismiss
  @State private var address = ""
  @State private var label = ""
  @State private var recipient = ""
  @State private var phone = ""
  let save: (String, String, String, String) -> Void
  var body: some View {
    NavigationStack {
      Form {
        TextField("Address", text: $address)
        TextField("Label", text: $label)
        TextField("Recipient", text: $recipient)
        TextField("Phone", text: $phone)
        Button("Add") { save(address, label, recipient, phone) }
      }
      .navigationTitle("Add stop")
      .toolbar { Button("Cancel") { dismiss() } }
    }
  }
}

// MARK: - Map

struct MapScreen: View {
  let stops: [StopRecord]
  @State private var route: RoutePath?
  @State private var showRecenter = false

  var body: some View {
    ZStack(alignment: .bottomTrailing) {
      MapViewRepresentable(stops: stops, route: route, recenter: showRecenter)
        .ignoresSafeArea(edges: .bottom)
      VStack(alignment: .trailing) {
        Button("Recalculate") { Task { await loadRoute() } }.buttonStyle(.bordered)
        Button("Recenter") { showRecenter.toggle() }.buttonStyle(.borderedProminent)
      }
      .padding()
    }
    .task { await loadRoute() }
  }

  private func loadRoute() async {
    let coords = stops.compactMap { stop -> (Double, Double)? in
      guard let lat = stop.latitude, let lon = stop.longitude else { return nil }
      return (lat, lon)
    }
    guard let origin = LocationProvider.shared.current() ?? coords.first, coords.count >= 2 else { return }
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
    for stop in stops {
      guard let lat = stop.latitude, let lon = stop.longitude else { continue }
      let annotation = MKPointAnnotation()
      annotation.coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lon)
      annotation.title = stop.label.isEmpty ? stop.address : stop.label
      map.addAnnotation(annotation)
    }
    if let route, route.points.count >= 2 {
      let coordinates = route.points.map { CLLocationCoordinate2D(latitude: $0.0, longitude: $0.1) }
      map.addOverlay(MKPolyline(coordinates: coordinates, count: coordinates.count))
    }
    if recenter, let location = map.userLocation.location {
      let region = MKCoordinateRegion(center: location.coordinate, latitudinalMeters: 3000, longitudinalMeters: 3000)
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
