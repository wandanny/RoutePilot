# RoutePilot for iPhone (SwiftUI) — v1.0.3

A native iOS rebuild of the Android courier route helper. It is **not** an APK wrapper. It is signed and sideloaded with a free Apple ID — no paid Apple Developer Program.

Design source of truth: `DESIGN.md`. Visual preview: `design-preview.html`. Product spec: `../SPEC.md`.

## Build

Push to GitHub. The workflow `.github/workflows/ios-build.yml` runs on a macOS runner:

1. Generates the Xcode project with XcodeGen from `project.yml`.
2. Runs unit tests for label parsing, CSV parsing and route ordering on the iOS simulator.
3. Builds an unsigned release app, packs an IPA and uploads it as `RoutePilot-unsigned-ipa`.

No Mac is needed on your side. The Windows development machine cannot compile Swift; CI is the real build check.

## Sideload with AltStore + AltServer

1. Install **AltServer** on your Windows PC and **AltStore** on the iPhone.
2. Sign in with your free Apple ID in AltStore.
3. Connect the iPhone by USB (or Wi-Fi with AltServer running) and trust the PC.
4. In AltStore, **Install from URL** with the GitHub release URL, or share the IPA from Files.
5. Free certificates expire after **7 days**. Keep AltServer running and use **Refresh All** in AltStore at least once a week.

## Included in v1.0

- Four tabs: Route, Scan, Map, Archive. Exceptions are the Failed filter on Route.
- AI-first label reading via lucif.ai with automatic on-device Apple Vision fallback, editable confirmation and a source badge.
- Nominatim geocoding with AI coordinate fallback; estimated coordinates are flagged.
- OSRM road-duration route optimization with open-path ordering and "Optimize now".
- Delivery windows editable in Add Stop and stop details; local notification 30 minutes before a window opens.
- Manual reorder by drag in the list and up/down buttons in the detail sheet.
- In-app MapKit map with numbered markers, route polyline and a glass stats panel.
- Google Maps handoff in chunks of nine stops.
- Done / Failed with reason, exceptions, archive, summary, reset with confirmation.
- JSON backup and restore via the Route options sheet. Data stays local; no account, backend or sync.
- Custom app icon, iOS-specific route-line loading animation, full light/dark mode, Dynamic Type, VoiceOver and 48 pt targets.

v1.0.3 adds an optional TomTom key (Route options → Maps settings) for better Malaysia address geocoding and road routing, with Apple/Nominatim/OSRM as fallbacks. A benchmark set of 20 public Taiping addresses with known coordinates is in `benchmark/taiping-addresses.csv`. v1.0.2 added Apple-first geocoding with an optional 50 km area restriction (off by default so public test labels from other cities still work), silent launch-time optimization, a straight-line map fallback when OSRM is down, correct solver ordering when some stops are unresolved, and permission requests held on the loading screen. CSV export is intentionally out of scope for v1.0.

## Distribution

Free AltStore sideloading from a GitHub release is the final channel. TestFlight and the App Store require the paid Apple Developer Program, which this project deliberately avoids.
