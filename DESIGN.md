# RoutePilot Design System

## Visual Theme & Atmosphere
- Mood: `professional_minimal`
- Feel: clean, confident, restrained. A working tool for a courier, not a marketing page.
- Direction: warm light surfaces, charcoal text, one considered orange accent, generous tap targets, no decorative noise.
- Excluded: gradients, stock photos, AI-cliché copy.

## Color Palette & Roles
- Background: `#F7F5F1` (warm off-white)
- Surface: `#FFFFFF`
- Surface muted: `#EDE9E2`
- Dark surface (route sheet / chips): `#262625`
- Text primary: `#1F1D1A`
- Text secondary: `#6B6560`
- Accent fill: `#F97316` (coral)
- Accent text / pressed: `#C2410C` (darker coral for WCAG AA on light surfaces)
- On accent: `#FFFFFF`
- Error: `#B3261E`
- Success: `#2E7D32`
- Divider: `#1F1D1A` at 12% opacity

Only one accent hue is used. The darker coral is for text and small icons where contrast matters; the brighter coral is for fills and decorative marks.

## Typography Rules
- Display: Outfit, 600, 32sp / 36sp line height, -0.02em tracking
- Headline: Outfit, 600, 24sp / 28sp
- Title: Outfit, 500, 18sp / 24sp
- Body: Satoshi, 400, 14sp / 20sp
- Label: Satoshi, 500, 12sp / 16sp, +0.02em tracking
- Mono (numbers, postcodes, ETAs): JetBrains Mono, 500, 14sp, tabular figures
- Both Outfit and Satoshi are bundled as app assets; no runtime font download.

## Component Stylings
- Buttons: 48dp minimum height, 16dp radius, filled accent or tonal outline; pressed state reduces opacity to 0.88 and scales 0.98.
- Cards: surface colour, 16dp radius, no border; elevation only when it communicates hierarchy.
- Inputs: 12dp radius, surface-muted fill, 1dp divider stroke, 48dp height.
- Bottom navigation: 72dp height, four destinations — Route, Scan, Exceptions, Archive; active item uses accent text and a short indicator.
- Chips: pill shape, surface-muted fill, 12sp label.
- Progress: linear indicator in accent fill with Track colour surface-muted.
- Snackbars: dark surface with light text, no exclamation marks in copy.

## Layout Principles
- Layout model: `single_column`
- Content padding: 16dp horizontal, 20dp between major blocks
- Section spacing: 24dp
- No max-width constraint needed on phone; content is edge-to-edge with safe-area insets.
- Bottom sheet for the ordered stop list; hero panel above it for progress and next stop.
- Large tap targets: 48dp minimum, 8dp minimum spacing between adjacent controls.

## Depth & Elevation
- Shadows: none decorative; at most 2dp elevation on the bottom sheet.
- Borders: 1dp divider on inputs only.
- No gradients. No coloured shadows. Lighting direction irrelevant without shadows.

## Do's and Don'ts
- DO use the declared tokens exclusively.
- DO keep one accent hue and one accent family.
- DO write short, direct copy ("Connection failed. Tap to retry.").
- DO show empty, loading, error and offline states with actions.
- DON'T invent colours outside the palette.
- DON'T use gradients, stock photos, exclamation marks or AI clichés.
- DON'T use more than two typefaces (Outfit and Satoshi; JetBrains Mono is a utility face for numbers).

## Responsive Behavior
- Phone portrait first. Landscape keeps the hero compact and moves the stop list to a full sheet.
- Tablets are out of scope for v1 but would use a two-pane layout later.
- Text scales with system font size; use `sp` everywhere.

## Non-happy states
- Empty: "No stops yet. Import a CSV or scan a label to get started." with both actions.
- Loading: logo pulse plus wordmark and tagline.
- Error: "Couldn't read the label. Try again or enter the address manually."
- Offline: "You're offline. Stops are saved on this device; route optimisation will run when you reconnect."

## Agent Prompt Guide
- Use Material 3 components with the tokens above.
- Accent fill appears at most three times per screen.
- All interactive elements need a visible focus/pressed state.
- Do NOT add decorative shadows, gradients or images.
