# RoutePilot iOS — Design System (v1.0.3)

## Design read

A fresh iOS-first redesign of the courier route helper: **calm, high-contrast field utility**. Warm neutral surfaces, one burnt-orange accent, large legible type, no decoration. Designed to be read at a glance while moving, in daylight or at night. This document is the source of truth for the iOS app; `../DESIGN.md` remains the Android source of truth. The two share a palette and a brand mark, not a widget set.

**Dials:** variance 4 / motion 3 / density 5. Restraint is a feature.

---

## 1. Visual theme and atmosphere

- Mood: professional, quiet, dependable. A working tool, not a marketing page.
- Light mode: warm off-white canvas, white cards, charcoal text, burnt-orange signal colour.
- Dark mode: near-black canvas, elevated dark cards, warm white text, the same accent.
- Glass is used **only on navigation chrome and overlays** (tab bar, sheets, map stats panel). Content surfaces are solid so legibility never depends on what is behind them.
- Excluded: gradients, coloured shadows, stock photography, decorative illustration, marketing clichés, exclamation marks in copy.

---

## 2. Colour tokens and roles

| Token | Light | Dark | Role |
|---|---|---|---|
| `canvas` | `#F7F5F1` | `#151514` | Screen background |
| `surface` | `#FFFFFF` | `#1E1E1D` | Cards, inputs, solid panels |
| `surfaceMuted` | `#EDE9E2` | `#2A2927` | Hero panel, chips, inactive tracks |
| `charcoal` | `#262625` | `#101010` | Dark sheets, map chrome base |
| `textPrimary` | `#1F1D1A` | `#F7F5F1` | Primary text |
| `textSecondary` | `#6B6560` | `#B9B2AA` | Supporting text, captions |
| `accentFill` | `#F97316` | `#F97316` | Filled buttons, active indicators, map route |
| `accentText` | `#C2410C` | `#F97316` | Accent text and small icons (AA contrast) |
| `onAccent` | `#FFFFFF` | `#1F1D1A` | Text on accent fill |
| `error` | `#B3261E` | `#FFB4AB` | Failed state, validation |
| `success` | `#2E7D32` | `#81C784` | Done state |
| `divider` | `#1F1D1A` @ 12% | `#FFFFFF` @ 12% | Hairlines |

Rules:
- One accent hue family only. Bright `#F97316` for fills; darker `#C2410C` for text on light surfaces.
- Accent appears at most three times per screen. If everything is orange, nothing is.
- Status is never colour-only: Done / Failed / Pending always carry a symbol or word as well.

### Glass recipe (chrome only)

- Tab bar and sheets: `ultraThinMaterial`, 1 pt `divider` stroke, no shadow.
- Map stats panel: `regularMaterial` with charcoal tint at 85% opacity for contrast over map tiles.
- Never place body text smaller than 17 pt on glass; never stack glass on glass.

---

## 3. Typography

System **SF Pro** only (no bundled font). All styles support Dynamic Type; sizes below are the default at Large content size.

| Style | Font | Size / line | Weight | Tracking |
|---|---|---|---|---|
| Large title | SF Pro Display | 34 / 42 | bold | -0.03em |
| Hero number | SF Pro Display | 44 / 50 | bold, tabular | -0.03em |
| Headline | SF Pro Text | 22 / 28 | semibold | -0.02em |
| Title | SF Pro Text | 18 / 25 | semibold | -0.01em |
| Body | SF Pro Text | 17 / 25 | regular | 0 |
| Subheadline | SF Pro Text | 16 / 23 | regular | 0 |
| Caption | SF Pro Text | 14 / 20 | regular | +0.01em |
| Numeric | SF Pro Text | 17 / 25 | medium, tabular | 0 |
| Button label | SF Pro Text | 17 / 24 | semibold | 0 |

- Type must breathe: body line height is never below 1.45×, and at least 12 pt separates a heading from the line beneath it.
- Postcodes, distances, durations and stop numbers use tabular figures so values do not jitter.
- Never truncate a recipient name or address on the confirm screen; allow wrapping or scroll.

---

## 4. Layout and spacing

- 8 pt grid. 20 pt horizontal page inset; 24 pt between major blocks; 32 pt between sections.
- 20 pt card padding; 12 pt gap between cards; 12 pt gap between inline controls.
- 20 pt card corner radius; 12 pt input radius; pill radius for chips and status badges.
- 48 pt minimum touch target for buttons and inputs (44 pt absolute floor for small controls).
- Phone portrait first; landscape keeps the hero compact and lets the stop list expand.
- Respect safe-area insets; content never sits under the home indicator or Dynamic Island.

---

## 5. Components

**Primary button** — accent fill, white/on-accent label, 48 pt height, 16 pt radius. Pressed: opacity 0.88 and scale 0.98. Disabled: `surfaceMuted` fill and `textSecondary` label.
**Secondary button** — `surface` fill, 1 pt divider stroke, `textPrimary` label, same geometry.
**Text button** — `accentText` label, no fill, used only for tertiary actions in the options sheet.

**Stop card** — solid `surface`, 20 pt radius, 20 pt padding. Content order: sequence number (tabular, accent text), label or address (title), address (subheadline, secondary), then a **static mini-map preview** (one pin, no route line, 88 pt tall, 12 pt radius), then status pill. Unresolved addresses get a neutral "Location not found" placeholder instead of a pin. One primary action (Done) inline; everything else lives in the detail sheet. Cards grow with their content; the list uses a single scroll container and never sets a fixed height.

**Filter chips** — All / Pending / Done / Failed. Selected: charcoal fill with warm white text. Unselected: `surfaceMuted` fill with `textPrimary` text. Failed chip carries a small exclamation symbol.

**Status pill** — Pending (neutral dot + "Pending"), Done (check + "Done"), Failed (cross + "Failed"). Never colour alone.

**Input** — `surface` fill, 1 pt divider stroke, 12 pt radius, 48 pt height, `textPrimary` value, `textSecondary` placeholder. Validation message directly below in `error`, 14 pt.

**Tab bar** — glass, four destinations: Route, Scan, Map, Archive. SF Symbols: `map`, `camera`, `navigation`, `archivebox`. Active item: accent text plus a 3 pt accent indicator above the label. Inactive: `textSecondary`.

**Sheet** — glass background, 20 pt radius top corners, grabber, title, close button. Detail and options sheets use this.

**Progress hero** — solid `surfaceMuted`, 20 pt radius, 24 pt padding. "Today's progress" (title), `"N of M stops"` (hero number), current next stop label and address. A compact inline spinner and "Optimising route…" appear only while working.

**Map stats panel** — glass at bottom of map: distance, duration, stop count (tabular), then Recalculate and Recenter buttons.

**Message banner** — charcoal background, warm white text, bottom of screen above the tab bar, with a single action when recoverable. No exclamation marks in copy.

---

## 6. Navigation and information architecture

Four tabs: **Route, Scan, Map, Archive**.

- **Route** — progress hero, Scan / Navigate all / **Open in Google Maps** buttons, filter chips, stop cards. Ellipsis button opens the options sheet: Summary, Open in Google Maps, Backup, Restore, AI settings, "Restrict stops to current area" toggle, Reset.
- **Scan** — camera entry point and the AI/Vision scan flow.
- **Map** — full-screen MapKit route view.
- **Archive** — completed routes, read-only.

Exceptions are the **Failed** filter on the Route tab, not a separate tab. Backup, restore, summary, reset and AI settings live in the options sheet. CSV export is out of scope for v1.0.

---

## 7. Screen specifications

### Route tab
1. Small wordmark "RoutePilot" (headline) with the ellipsis menu button at trailing edge.
2. Progress hero.
3. Two primary buttons: **Scan label**, **Navigate all stops**.
4. Secondary row: **Import CSV**, **Add stop**, **Optimize now**.
5. Filter chips: All, Pending, Done, Failed.
6. Stop cards, sorted by `orderIndex`, each with a static mini-map preview. Unresolved addresses show "Address not found — tap to edit" in `error`; never an estimated-coordinate pin.
7. The whole tab is one `ScrollView`. The stop list is a `LazyVStack`, not a nested `List`; no fixed row height, no disabled scrolling.

### Maps handoff sheet
Google Maps allows at most nine waypoints per URL. Pending stops are split into batches of nine. The sheet is titled "Open route in Google Maps", shows "N batches of up to 9 stops", the current tally "Batch 1 of N", and buttons **Open batch** and **Next batch**. Each batch opens one URL with `travelmode=driving` and `dir_action=navigate`; unresolved stops contribute their address text, never fabricated coordinates.

### Scan flow
1. Camera or photo-library entry, with a rounded white guide frame.
2. Recognition runs AI-first. On missing key, failure or no connection, Apple Vision runs automatically.
3. Confirm screen: editable Recipient, Address, Postcode and Phone; a source badge — "Read by AI" or "Read on device"; warnings; "Save stop", "Retake", "AI settings".
4. First AI use shows a consent sheet naming lucif.ai, Nominatim and OSRM.
5. Duplicate tracking IDs offer "Add anyway" / "Skip".

### Stop detail sheet
Editable recipient, address, postcode, phone, window start/end and notes. Reorder via up/down buttons only. Actions: Navigate, Done, Failed (with reason and note), Pin.

### Map screen
Full-screen map with numbered burnt-orange markers and a polyline in `#C2410C`. Glass stats panel at the bottom. Recalculate rebuilds the OSRM route; Recenter returns to the user's location.

### Archive tab
List of archived routes with date and stop counts. Read-only.

### Loading screen
A route line draws itself between two pins over 800 ms. Both pins sit exactly at the path endpoints: lower-left `(24, 104)` and upper-right `(166, 22)` in the 190 × 120 animation frame. The loading screen stays up while location and notification permissions are requested and answered, with an 800 ms minimum; then it cross-fades into the Route tab. Wordmark "RoutePilot" and the tagline "Your route, sorted." sit below the mark. A denied permission never blocks entry — one short guidance message appears after launch. No infinite loops.

### App switcher cover
When the app enters the background, a branded cover replaces the last screen: charcoal background, the chosen logo mark, wordmark "RoutePilot" and "Your route, sorted." This makes the switcher card show the brand and hides address data. It disappears when the app becomes active again.

---

## 8. Motion

- Route-line draw: 800 ms, ease-out, trim from 0 to 1; pins fade in at the endpoints.
- Cross-fade to Route tab: 200 ms.
- Press: opacity 0.88, scale 0.98, 120 ms.
- Sheet presentation: standard iOS spring; no custom bouncing.
- No looping animations, no parallax, no confetti, no shimmer except the active optimisation spinner.

---

## 9. States and copy

| State | Copy |
|---|---|
| Empty | "No stops yet. Import a CSV or scan a label to get started." |
| Loading | "Your route, sorted." |
| Optimising | "Optimising route…" |
| Error (scan) | "Couldn't read the label. Try again or enter the address manually." |
| Offline | "You're offline. Stops are saved on this device; route optimisation will run when you reconnect." |
| Success (save) | "Stop added." |
| Unresolved address | "Address not found — tap to edit." |
| Maps batches | "N batches of up to 9 stops." |
| Reset confirm | "Pending N · Done N · Failed N. The route will be archived and a new empty route started." |

Keep sentences short and literal. No exclamation marks, no "Oops", no emoji.

---

## 10. Feature behaviour in v1.0

- **Recognition**: AI first via lucif.ai; automatic Apple Vision fallback on missing key, error or offline. Source badge on confirm.
- **Geocoding**: TomTom first when a user-owned key is set in Maps settings (free, 20k/mo, Malaysia address points/house numbers), then Apple `CLGeocoder`, then Nominatim fallback; no AI coordinates at all. "Restrict stops to current area" is OFF by default so test labels from other cities still work; when ON, results more than 50 km from current GPS are rejected and Nominatim uses structured Taiping/Perak fields. Unresolved stops stay in the list with a badge and no map pin. On upgrade, previously AI-estimated coordinates are cleared and re-geocoded.
- **Optimisation**: OSRM road durations; open-path solver; end at the farthest efficient stop. Solver indices map to the geocoded subset only; unresolved stops are appended at the end in original order. Launch-time optimization is silent — result messages appear only on manual "Optimize now" / "Navigate all". If OSRM is unavailable the map draws a straight polyline through the geocoded stops.
- **Delivery windows**: editable on Add Stop and detail; local notification 30 minutes before a window opens; permission requested on first windowed save.
- **Reorder**: up/down buttons in the detail sheet only, so the list can scroll naturally.
- **Maps handoff**: a visible Route button plus an options entry opens a batch sheet; batches of 9 are opened one at a time with a tally, using `travelmode=driving` and `dir_action=navigate`.
- **Data**: local SwiftData only. JSON backup and restore via the options sheet. No account, no sync, no backend.

---

## 11. Accessibility

- Full Dynamic Type support; layouts must survive Accessibility Large without clipping.
- VoiceOver labels on every control; stop cards announce label, address, sequence and status as one element.
- 48 pt minimum targets for buttons and inputs; reorder has explicit buttons, not drag alone.
- Contrast: body text meets WCAG AA on both themes; accent text uses `#C2410C` on light surfaces.
- Status conveyed by word and symbol, never colour alone.
- Reduce Motion: replace the route-line draw with a simple cross-fade.

---

## 12. App icon

Source artwork: `design-assets/icon-source.png` (the chosen parcel + route-pin mark, concept 2). Render as an iOS rounded-square icon on a charcoal `#262625` background, with the mark in burnt orange `#C2410C` and the parcel outline in warm white. No text, no gradients, no effects. Must remain recognisable at 60 pt.

---

## 13. Do and don't

**Do:** use the declared tokens; keep content surfaces solid; give every action a visible pressed state; design empty, loading, error and offline states with a next action; keep copy short.

**Don't:** invent colours; put glass behind body text; stack glass on glass; use gradients, shadows or stock images; add decorative animation; use colour as the only status cue; ship an unlabelled icon button.

---

## 14. Acceptance checklist for v1.0.2

- [ ] CI build green from the release commit, producing a valid unsigned IPA.
- [ ] Unit tests green for label parsing, CSV parsing and route ordering.
- [ ] Route tab: scan, add, import, filter, done/failed, reset and options sheet all work.
- [ ] Scanned stops attach to the active route and appear immediately.
- [ ] Vision fallback works with no AI key and shows the correct source badge.
- [ ] Delivery-window notification fires 30 minutes early.
- [ ] Up/down reorder persists; the stop list scrolls to the last card at Accessibility Large.
- [ ] No AI-estimated coordinates: Nominatim/Apple only; old bad pins cleared on upgrade.
- [ ] Each stop card shows a one-pin mini map or an unresolved placeholder.
- [ ] Maps batch sheet opens every chunk with a visible tally.
- [ ] Backup and restore round-trip on device.
- [ ] Light and dark mode, Dynamic Type and VoiceOver checked on iPhone.
- [ ] Custom icon present; loading animation runs 800 ms then cross-fades.
- [ ] Reset route shows a confirmation with counts and actually archives/starts a new route.
- [ ] Loading pins sit exactly on the drawn route endpoints.
- [ ] App switcher shows the branded cover.
- [ ] No routing warning appears on launch; straight-line map fallback works.
- [ ] Area restriction toggle defaults off and rejects far results when on.
- [ ] Permissions are requested during loading and denial does not block entry.
- [ ] Full device walkthrough completed by the owner.
