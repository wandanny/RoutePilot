# RoutePilot for iPhone (SwiftUI) — v1.1.0

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

## Included in v1.1.0

- Four tabs: Route, Scan, Map, Archive. Exceptions are the Failed filter on Route.
- AI-first label reading via lucif.ai with automatic on-device Apple Vision fallback, editable confirmation and a source badge.
- Local-first geocoding: bundled 5,944-entry OSM address index, then an optional TomTom key, then Apple `CLGeocoder` / Nominatim. No AI-estimated coordinates.
- TomTom or OSRM road-duration route optimization with open-path ordering and "Optimize now"; straight-line map fallback when routing is unavailable.
- Delivery windows editable in Add Stop and stop details; local notification 30 minutes before a window opens.
- Manual reorder with up/down buttons in the detail sheet.
- In-app MapKit map with numbered markers, route polyline and a glass stats panel.
- Google Maps handoff in chunks of nine stops.
- Done / Failed with reason, exceptions, archive, summary, reset with confirmation.
- JSON backup and restore via the Route options sheet. Data stays local; no account, backend or sync.
- Custom app icon, iOS-specific route-line loading animation, full light/dark mode, Dynamic Type, VoiceOver and 48 pt targets.

v1.0.3 adds an optional TomTom key (Route options → Maps settings) for better Malaysia address geocoding and road routing, with Apple/Nominatim/OSRM as fallbacks. A benchmark set of 20 public Taiping addresses with known coordinates is in `benchmark/taiping-addresses.csv`; run `TOMTOM_API_KEY=... python ios/benchmark/test_tomtom.py` to measure match accuracy. v1.0.4 corrects the TomTom Matrix request to the verified POST form. v1.0.5 bundles a 5,944-entry offline OSM address index for Taiping/Kamunting/Simpang, tries it before any online geocoder, permanently caches successful lookups, checks for index updates weekly (manual button in Maps settings), and carries ODbL attribution. v1.0.2 added Apple-first geocoding with an optional 50 km area restriction (off by default so public test labels from other cities still work), silent launch-time optimization, a straight-line map fallback when OSRM is down, correct solver ordering when some stops are unresolved, and permission requests held on the loading screen. v1.0.7 adds an optional per-route **Lock route order** switch in Route options. While locked, geocoding still runs but no re-ordering happens, new stops append, pin/reorder are disabled, "Optimize now" asks before unlocking, and the Maps batch sheet confirms batches follow the saved order. CSV export is intentionally out of scope for v1.0. v1.1.0 bundles the shared enriched 8,754-entry offline index (OSM, Overture Places, MCMC/GeoNames), the same index shipped in Android v2.0.0; the v1.0.7 Lock route order switch remains available.

## Distribution

Free AltStore sideloading from a GitHub release is the final channel. TestFlight and the App Store require the paid Apple Developer Program, which this project deliberately avoids.
