# RoutePilot agent conventions

- Source of truth for product decisions: `SPEC.md` in this directory.
- Single-module Android app under `app/`. Package root: `com.local.routepilot`.
- Layers: `domain` (pure Kotlin, no Android imports), `data` (Room, CSV, backup, repository), `ui` (Compose, ViewModel), `notifications` (WorkManager).
- All persistence is local and offline-first. Do not introduce a network client, account system or remote API without a spec change.
- Domain code must stay testable on the JVM. Put decision logic in `domain`, not in Compose or the ViewModel.
- Tests assert observable behaviour through public entry points. Prefer JVM unit tests over emulator tests.
- Kotlin style: 2-space indent, no wildcard imports, explicit types on public functions.
- Do not add proof-of-delivery (scan, photo, signature); it is explicitly out of scope for v1.
