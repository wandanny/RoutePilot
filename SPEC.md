# RoutePilot — Specification

Status: v1 approved and shipped as a debug APK; v2 approved by owner (label scanning + UI redesign).
Source of decisions: `grill-me` / `grilling` interviews.

## Problem Statement

As a courier working from an Android phone, I receive parcels with printed labels and a list of stops. Typing hundreds of addresses by hand is slow and error-prone. My current app works offline, but its UI is plain and finding the next stop in a long list is awkward. When a customer is unavailable I need to record why, and when a parcel cannot be delivered I need to know whether I already scanned it.

## Solution

A single-player, offline-first Android app that imports a daily stop list from CSV **or by photographing individual parcel labels**, works out a sensible order, hands stops off to the driver's preferred navigation app, and keeps a reliable local record of deliveries and failures. v2 adds rapid on-device label scanning with an editable confirmation step and a redesigned route screen based on the owner's visual reference.

## User Stories

### v1 — route helper (shipped)

1. As a courier, I want to import a CSV list of stops so that I do not have to retype addresses each morning.
2. As a courier, I want the importer to accept comma- or semicolon-separated files so that regional spreadsheet exports work.
3. As a courier, I want malformed rows reported so that I can fix my file before starting.
4. As a courier, I want to add a stop manually so that I can handle same-day additions.
5. As a courier, I want each stop to carry address, label, recipient, phone, notes, priority, time window and optional coordinates so that all information is in one place.
6. As a courier, I want to use supplied latitude/longitude when present and fall back to address strings otherwise so that partial data is usable.
7. As a courier, I want stops ordered by a nearest-neighbour calculation from my current location so that I spend less time driving.
8. As a courier, I want priority and time windows to influence ordering so that urgent deliveries are handled early.
9. As a courier, I want the order recomputed after each completed stop so that it reflects where I actually am.
10. As a courier, I want to pin a stop as next and drag stops to reorder so that I can override the computed order.
11. As a courier, I want to hand a stop off to Google Maps, Waze or another installed map app through a chooser so that I can use the app I already have.
12. As a courier, I want to mark a stop done with one tap and undo mis-taps so that progress tracking is fast.
13. As a courier, I want to mark a stop failed with a reason, note and retry time so that undeliverable parcels are recorded accurately.
14. As a courier, I want failed stops in an Exceptions list that stays in route history so that they are not lost.
15. As a courier, I want a notification before a delivery window starts so that I do not miss it.
16. As a courier, I want one active route with an archive so that yesterday's work is available but out of the way.
17. As a courier, I want JSON backup/restore and CSV export so that device loss does not destroy my records.
18. As a courier, I want location and notification permissions explained before they are requested so that I understand why they are needed.

### v2 — label scanning and UI redesign (approved, in progress)

19. As a courier, I want to photograph a parcel label and have the address extracted on-device so that I do not type hundreds of addresses.
20. As a courier, I want a torch toggle and a guide frame so that wrinkled, low-contrast labels like my SPX sample are readable.
21. As a courier, I want SPX/Shopee labels parsed using the "Recipient Details / Penerima" section so that the sender's address is never mistaken for the delivery address.
22. As a courier, I want other label formats to fall back to generic OCR so that a different carrier does not block me.
23. As a courier, I want the tracking-ID barcode read as a duplicate key so that the same parcel is not added twice.
24. As a courier, I want a duplicate warning offering Skip or Add anyway so that I decide what happens.
25. As a courier, I want to confirm and edit recipient, address and postcode before saving so that OCR mistakes are caught.
26. As a courier, I want uncertain fields highlighted and the raw OCR text available when parsing fails so that I can still enter the stop.
27. As a courier, I want the camera to restart immediately after saving so that I can work through a pile of labels quickly.
28. As a courier, I want a session counter so that I know how many labels I have added.
29. As a courier, I want the label photo discarded after extraction so that no unnecessary personal data is stored.
30. As a courier, I want the app restyled with light-grey surfaces, rounded cards, pill chips, an orange accent and a dark bottom sheet so that it matches the visual direction I supplied.
31. As a courier, I want a hero panel showing route progress and the next stop, with the ordered stop list in a draggable bottom sheet, so that the important information is visible at a glance.
32. As a courier, I want light and dark themes driven by Material 3 colour tokens so that the app works in different lighting.

### v3 — opt-in cloud improvement (approved, in progress)

33. As a courier, I want an "Improve with AI" button on the confirm screen so that I can rescue a badly read label without typing the address.
34. As a courier, I want to enter my own lucif.ai API key once in an AI settings dialog so that no secret is shipped inside the app.
35. As a courier, I want the app to store that key encrypted on my phone so that it is not readable from the APK or app data.
36. As a courier, I want a one-time warning before the first cloud call so that I know the label image and address leave my device.
37. As a courier, I want only a cropped, downscaled version of the label sent so that the call is faster and cheaper.
38. As a courier, I want the cloud result to replace the extracted fields so that I can correct them afterwards in the same confirm screen.
39. As a courier, I want the on-device result kept if the cloud call fails or I have no signal so that I never lose the stop.
40. As a courier, I want a session counter for cloud improvements so that I can see how many calls I have made.

### v4 — AI-first scanning and optimized multi-stop route (approved, in progress)

41. As a courier, I want cloud AI to scan every label by default so that poor on-device OCR does not slow me down.
42. As a courier, I want on-device OCR used only when the cloud call fails or I have no signal so that scanning still works offline.
43. As a courier, I want a short note when AI was unavailable so that I know which method was used.
44. As a courier, I want addresses geocoded when a stop is added so that the route can be optimised before I leave.
45. As a courier, I want the route optimised on real road distances using OSRM so that I waste less time, fuel and energy.
46. As a courier, I want the route to end at the stop farthest from my start so that I do not zig-zag back.
47. As a courier, I want remaining stops re-optimised after each completed delivery so that the plan stays efficient.
48. As a courier, I want unresolved addresses appended at the end with a warning count so that they are never silently dropped.
49. As a courier, I want one prominent "Open optimized route" button on the hero panel so that the handoff is obvious.
50. As a courier, I want a confirmation sheet showing estimated distance, time and chunk count before Maps opens so that I know what to expect.
51. As a courier, I want Google Maps opened directly in driving navigation mode so that I do not have to copy links.
52. As a courier, I want routes longer than nine stops split into consecutive Maps routes so that Maps accepts all waypoints.
53. As a courier, I want the privacy dialog to name lucif.ai, Nominatim and OSRM so that I know who receives my data.

## Implementation Decisions

- **Stack**: Kotlin, Jetpack Compose (Material 3), Room, Koin, Navigation-free screen state, kotlinx.serialization, WorkManager. Single `app` module.
- **Offline-first**: all stop data stays local. No account, no backend, no remote storage.
- **Label recognition**: CameraX photo capture plus ML Kit Text Recognition with **bundled** on-device models — no network, no API key. The barcode/QR is read by ML Kit Barcode Scanning for the tracking ID only; it never supplies an address.
- **Label parsing**: pure-Kotlin parser. SPX labels are anchored on "Recipient Details / Penerima"; the sender section is ignored. 5-digit Malaysian postcodes are parsed. Phone is captured when present but is commonly absent on SPX labels. Generic fallback extracts the best-looking address block when the SPX anchor is missing.
- **Tracking ID**: stored on the stop as a dedicated field, used only for duplicate detection and backup.
- **Capture flow**: scan → confirm (editable, with raw OCR fallback) → save → camera restarts; photo is held in memory only and discarded.
- **Confidence**: fields are flagged when the parser could not find a postcode/name pattern or when text is unusually short; the driver must explicitly save.
- **Coordinates**: labels have no coordinates. Scanned stops are stored address-only and therefore sort after stops with coordinates in the current route engine. Navigation handoff still works from the address string.
- **Map**: embedded maps, MapLibre and geocoding are explicitly **out of scope** after evaluation. Keep the existing navigation-app chooser.
- **UI**: the owner's screenshot is a **visual style reference only**. Route screen is restructured to a hero panel (route progress + next stop) with pill chips (route name, progress) and a draggable dark bottom sheet containing the ordered stop list and its existing actions. Material 3 colour tokens with light/dark themes.
- **Operating area**: Taiping / Simpang / Kamunting, Perak, Malaysia. Sample data should use Malaysian addresses.
- **Optional cloud upgrade**: a driver-initiated "Improve with AI" action calls `POST https://lucif.ai/v1/chat/completions` with alias `lucif` and asks for strict JSON containing recipient name, address and postcode. The captured bitmap stays in memory only until the confirm screen closes; it is cropped to the recognised text bounds (fallback: guide frame), downscaled and compressed before upload. No photo is written to disk and no upload happens without the button press.
- **Key and consent**: the API key is entered in an AI settings dialog on the confirm screen and stored in EncryptedSharedPreferences. A one-time consent dialog explains that the image and address leave the device. The key is never compiled into the APK.
- **Cloud result handling**: replace all extracted fields, then let the driver edit and save as usual. Invalid JSON, a failed request or no connectivity keeps the on-device result and shows a short message. A session counter tracks cloud improvements; there is no hard cap.
- **AI-first scanning (v4)**: the cloud VLM runs on capture; on-device OCR is invoked only as a fallback. Consent copy is reworded because cloud is now the default. No cap on calls.
- **Route optimisation (v4)**: addresses are geocoded via Nominatim on import/scan and cached in the Stop's coordinate fields. OSRM's table service supplies road distances; a local open-path TSP solver orders stops, starting at current GPS (fallback: first stop) and ending at the stop farthest from the start. Remaining stops are re-optimised after each completion using cached road data. Failed geocodes are appended last with a visible warning count.
- **Maps handoff (v4)**: Google Maps URLs are opened directly with `travelmode=driving` and `dir_action=navigate`. Google Maps URLs do not optimise waypoints, so ordering happens in the app before handoff. Routes are split into chunks of at most nine stops. A confirmation sheet shows total distance, time and chunk count. Offline or failed optimisation falls back to the existing straight-line order.
- **Privacy (v4)**: the one-time consent dialog lists lucif.ai (label AI), Nominatim (geocoding) and OSRM (route optimisation).
- **UI redesign (v4)**: full redesign governed by `DESIGN.md` — professional_minimal mood, light warm base, coral accent, Outfit/Satoshi bundled fonts, single-column compact layout, bottom navigation, and designed empty/loading/error/offline states. The redesign-skill audit drives fixes; no framework change.

## Testing Decisions

A good test asserts externally observable behaviour through a public entry point, never internal helpers. The label parser is the main pure-Kotlin seam, tested with OCR text transcribed from the real SPX sample (recipient section, sender section, wrapped address lines, postcode) plus a generic label and an unreadable case. Route-engine and CSV-parser tests remain. Repository tests use an in-memory Room database. UI tests cover the confirm screen's required edits and duplicate warning. Camera hardware stays out of JVM tests.

## Out of Scope

- Barcode/QR as an address source, proof-of-delivery (scan-at-door, photo, signature), backend/accounts/multi-driver dispatch.
- Embedded maps, MapLibre, Google Maps SDK, geocoding, turn-by-turn navigation, traffic and road-network routing.
- Real `.xlsx` import, cloud extraction with no network or no key (OCR fallback covers it), Wear OS, tablets, Play Store publishing and release signing.
- Storing label photos on disk, logging API keys or images, and sending data to any service other than the driver-configured lucif.ai endpoint.
- Reading sender addresses for returns; only the recipient address becomes a stop.
