# RoutePilot for iPhone (SwiftUI)

This is a native iOS rebuild of the Android app. It is **not** an APK wrapper. It is signed and sideloaded with a free Apple ID — no paid Apple Developer Program.

## Build the unsigned IPA

1. Push this repository to GitHub (public repo keeps CI free).
2. The workflow `.github/workflows/ios-build.yml` runs on a macOS runner, generates the Xcode project with XcodeGen, builds an unsigned app, packs an IPA and uploads it as an artifact.
3. Download `RoutePilot-unsigned-ipa` from the Actions run.

No Mac is needed on your side.

## Sideload with AltStore + AltServer

1. Install **AltServer** on your Windows PC and **AltStore** on the iPhone.
2. Sign in with your free Apple ID in AltStore.
3. Install AltServer's Mail plugin if prompted on macOS (Windows uses the AltServer app directly).
4. Connect the iPhone by USB or Wi‑Fi with AltServer running.
5. In AltStore, **+ → Install** the downloaded `RoutePilot.ipa`.
6. Free certificates expire after **7 days**. Keep AltServer running and use **Refresh All** in AltStore at least once a week, or the app will stop opening.

## What's included

Core parity: label scanning through AI with editable confirmation, AI settings stored in the Keychain, route list, CSV import, add stop, Nominatim + AI geocoding with estimated-coordinate flags, OSRM optimization, Optimize now, done/failed states, exceptions, archive, summary, in-app MapKit map, Google Maps handoff, JSON backup/restore, reset with confirmation.

Data stays local on each device; move it between phone and tablet with the backup file. No backend, no accounts, no proof-of-delivery photos or signatures.

## Known caveats

- The iOS code cannot be compiled on the Windows development machine. The macOS CI runner is the first real compile check; expect to iterate on build errors there.
- Apple Vision is not yet wired into the fallback path in this first pass — on-device OCR fallback is still to be added after the initial build succeeds.
- Not yet ported: CSV export of history, delivery-window notifications, per-stop manual reorder, and the Android loading-screen animation. These are planned for the parity pass after the first installable build.
- AltStore's 7-day re-signing is mandatory with a free Apple ID.
