# RoutePilot

## Platforms
- Android: native Kotlin app, built with Gradle in `app/`.
- iPhone: native SwiftUI rebuild in `ios/`, built unsigned on GitHub Actions and sideloaded with AltStore + a free Apple ID. See `ios/README.md`.

Offline-first route helper for a courier on an Android phone. Built from the approved spec in `SPEC.md`.

## What it does (v2)

- **Scans parcel labels** with the phone camera. On-device ML Kit OCR reads the delivery address (SPX/Shopee labels are anchored on "Recipient Details / Penerima"), and the barcode is read only as a tracking-ID duplicate key. Nothing is uploaded.
- **Confirms before adding**: recipient, address, postcode and phone are editable; low-confidence fields are flagged and the raw recognised text is shown when parsing fails. The photo is discarded after extraction.
- Keeps the v1 route features: CSV import, offline local storage, nearest-neighbour ordering with priority/time-window/pin overrides, Maps/Waze handoff, done/failed states, exceptions, archive, JSON backup and CSV export.
- Redesigned UI: orange accent, light-grey hero panel with route progress and next stop, and a draggable dark bottom sheet with the stop list. Light and dark themes.
- **Optional AI upgrade**: on the confirm screen, "Improve with AI" sends a cropped, downscaled label to the `lucif` model and fills the fields from its JSON response. The key is entered in "AI settings" and stored encrypted on-device; nothing secret is in the APK. A one-time consent dialog explains that the image leaves the phone. If the call fails, the on-device result is kept.
- **Branding**: a flat parcel + route-pin logo (concept 2 in `logo-concepts/`) is used for the launcher icon and an opening loading screen with a subtle pulse, the wordmark and "Your route, sorted." The loading screen waits for local data with an 800 ms minimum.
- **AI-first scanning (v4)**: the cloud VLM scans every label on capture; on-device OCR is used only if the cloud call fails or there is no signal, with a note explaining the fallback.
- **Optimized route (v4)**: addresses are geocoded via Nominatim and cached; if that fails, lucif estimates coordinates from the plain address (flagged "AI estimated" on the stop card). OSRM road distances plus a local open-path TSP produce the order, ending at the farthest stop. Every optimization takes a fresh GPS fix first, and the remaining route is re-optimised after each completion. The Route tab has an "Optimize now" button for a manual full re-plan. The "Maps" button opens Google Maps in driving navigation mode with routes longer than nine stops split into consecutive chunks.
- **Redesign (v4)**: full UI overhaul per `DESIGN.md` — warm light palette, coral accent, bundled Outfit + Satoshi fonts, compact single-column layout, bottom navigation, and designed empty/loading/error/offline states.
- **In-app map**: a fifth "Map" tab shows all pending stops as markers, an OSRM road route line, live GPS with recenter, and a step list that auto-advances. Map tiles use standard OpenStreetMap tiles (no key) and are cached as loaded. The map has a fixed 300dp compact panel and a full-screen mode in the Map tab. The Google Maps handoff remains available via the "Maps" button.
- **Route actions**: "Navigate all stops" re-optimizes pending stops and opens the in-app map full-screen; a stop's Navigate button opens the map focused on that stop. "Reset" archives the current route (stops kept read-only) and starts a new empty route after a confirmation showing status counts.

## What v1 did

- Imports a CSV stop list (comma or semicolon delimited) and lets you add stops by hand.
- Orders stops by nearest-neighbour from your current GPS position, with priority, time windows, pin-next and manual reordering.
- Hands a stop off to Google Maps, Waze or any installed map app through a chooser.
- Marks stops done with undo, or records a failed delivery with a reason, note and retry time.
- Keeps one active route plus an archive, with JSON backup/restore and CSV export.
- Notifies you shortly before a delivery window starts.

No account, no backend, no network requirement, no scanning or photo proof (all out of scope).

## Build

Requires JDK 21 and Android SDK platform 36.

```powershell
cd app
.\gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Debug-signed, so it can be sideloaded directly onto a device or installed on an emulator:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Sample data

`sample-stops.csv` in this directory demonstrates all supported columns, including optional coordinates.

## Tests

```powershell
.\gradlew.bat test
```

## Emulator on this machine

The `medium_phone` AVD boots with AEHD hardware acceleration and GPU disabled (the Play Store image and host GPU caused an offline adb state here):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd medium_phone -gpu off
```

First boot takes about 100 seconds. Check with `adb devices` until it says `device` rather than `offline`.
