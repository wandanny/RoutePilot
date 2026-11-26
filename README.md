# RoutePilot

Offline-first courier route helper for a small delivery operation around Taiping, Kamunting and Simpang, Perak. There are two native apps in this repository:

| Platform | Location | Status |
|---|---|---|
| Android | `app/` | Native Kotlin/Compose app, debug builds installed and tests passing |
| iPhone | `ios/` | Native SwiftUI app, v1.0.6, unsigned IPA built on GitHub Actions and sideloaded with AltStore |

Product decisions are in `SPEC.md`. Visual systems are in `DESIGN.md` (Android) and `ios/DESIGN.md` (iOS), with a preview at `ios/design-preview.html`.

## What both apps do

- Scan a parcel label with the camera. AI reads it first; on-device OCR is the automatic fallback when the AI key is missing, a request fails, or there is no signal. You confirm the recipient, address, postcode and phone before anything is saved.
- Keep stops in a local, offline-first store. No account, no backend, no sync, no proof-of-delivery photos or signatures.
- Order stops with road durations, ending at the farthest efficient stop. "Optimize now" re-plans on demand, and the order is refreshed as stops are completed.
- Show an in-app map with numbered stops, a route line and recenter, plus a Google Maps handoff that splits long routes into batches of nine stops.
- Record Done / Failed with reasons, exceptions and an archive; review a summary; reset a route after confirmation.
- Support delivery windows with a notification 30 minutes before a window opens.
- Back up and restore all data as a local JSON file. Data never leaves the device except for an explicitly enabled AI scan or an online geocode lookup.

## Android

- Kotlin, Jetpack Compose (Material 3), Room, Koin, CameraX + ML Kit, WorkManager, single module.
- Parcel labels are parsed from "Recipient Details / Penerima"; the barcode is used only as a duplicate tracking-ID key.
- Geocoding uses Nominatim with an AI coordinate fallback that is flagged on the stop card; routing uses OSRM road durations plus a local open-path solver.
- Includes CSV import/export, drag/pin/manual reorder, Waze/Google Maps chooser, light/dark themes and an opening loading animation.
- AI requests use your own lucif.ai key, stored encrypted on-device, behind a one-time consent sheet. The label image is cropped and downscaled before sending.

Build (JDK 21, Android SDK platform 36):

```powershell
cd app
.\gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Download the published debug APK from the [v1.0-android release](https://github.com/wandanny/RoutePilot/releases/download/v1.0-android/RoutePilot-v1.0.apk). It is debug-signed, so it can be sideloaded directly.

Install on a connected device or emulator:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Run JVM unit tests:

```powershell
cd app
.\gradlew.bat test
```

## iPhone

SwiftUI, SwiftData, MapKit, iOS 17+, no third-party SDK. Built unsigned by `.github/workflows/ios-build.yml` on a macOS runner, then signed on your iPhone with AltStore and a free Apple ID. No paid Apple Developer Program, no Mac required locally.

Key features in v1.0.6:

- Four tabs: Route, Scan, Map, Archive. Exceptions are the Failed filter on Route.
- AI-first scanning with automatic Apple Vision fallback, editable confirmation, and a "Read by AI" / "Read on device" badge.
- **Local-first geocoding**: a bundled 5,944-entry OpenStreetMap address index for Taiping/Kamunting/Simpang is tried first. Matches are conservative (house number + street, postcode when present); ambiguous addresses fall through to an online geocoder.
- Optional free TomTom key (Route options → Maps settings) for better Malaysia address coverage and road routing. Apple `CLGeocoder` and Nominatim remain fallbacks; OSRM and a straight-line polyline are used when routing is unavailable.
- Every successful geocode is cached permanently on-device. The index checks for updates weekly, with a manual check and cache-clear control in Maps settings.
- MapKit map with numbered markers, route polyline and a glass stats panel; batched Google Maps handoff with a visible batch tally.
- Delivery windows in Add/Edit, 30-minute notifications, up/down reorder in the detail sheet, JSON backup/restore, custom app icon, route-line loading animation, full light/dark mode, Dynamic Type and VoiceOver.
- CSV export is intentionally out of scope on iOS.

Download the current unsigned IPA from the [v1.0.6 release](https://github.com/wandanny/RoutePilot/releases/download/v1.0.6-ios/RoutePilot-v1.0.6.ipa). In AltStore, use **Install from URL** with that link, or share the IPA from Files.

Because the signing certificate is free, it expires after **7 days**. Keep AltServer running on the PC and use **Refresh All** in AltStore at least weekly, or the app will stop opening. See `ios/README.md` for full sideload instructions.

## Build and test the iPhone app

No Mac is needed locally. Push to GitHub; the workflow generates the Xcode project with XcodeGen, runs unit tests on an iOS simulator, builds an unsigned release app, packs the IPA and uploads it as the `RoutePilot-unsigned-ipa` artifact. CI is the only Swift compile check — the Windows development machine cannot build iOS code.

Unit tests cover label parsing, CSV parsing, the route solver and area validation: 5 tests passing in the current release build.

## Repository layout

- `SPEC.md` — approved product spec and version history.
- `DESIGN.md`, `brief-preview.html` — Android visual system.
- `ios/DESIGN.md`, `ios/design-preview.html` — iOS visual system and preview.
- `app/` — Android Gradle project.
- `ios/RoutePilot/` — SwiftUI sources; `ios/data/` and `ios/benchmark/` contain the generated address index, its version file and the benchmark scripts.
- `logo-concepts/` — brand mark concepts; concept 2 is the chosen mark.
- `sample-stops.csv` — example CSV import file for Android.

## Licence and attribution

Route data is stored locally and is never sold or synced. The bundled offline address index is derived from OpenStreetMap and is used under the Open Database Licence; attribution appears in the iOS Maps settings and Summary screens. Map tiles and routing data carry their own provider terms.

## Out of scope

No backend, accounts, cloud sync, proof-of-delivery photos/signatures, real `.xlsx` support, Wear OS, tablets, or paid Apple Developer Program features such as TestFlight/App Store distribution.
