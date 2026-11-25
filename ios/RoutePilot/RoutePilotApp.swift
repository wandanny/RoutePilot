import SwiftUI
import SwiftData

@main
struct RoutePilotApp: App {
  var body: some Scene {
    WindowGroup {
      ContentView()
    }
    .modelContainer(for: [RouteRecord.self, StopRecord.self], isAutosaveEnabled: true)
  }
}
