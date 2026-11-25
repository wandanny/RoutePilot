# Architecture map

```
app/
  domain/          Stop, RouteInfo, status enums, RouteEngine (pure Kotlin)
  data/            CsvParser, Room entities/DAO/database, RouteRepository, backup payload
  ui/main/         MainScreen (Compose), MainScreenViewModel, UI state
  ui/theme/        Material 3 theme
  notifications/   WindowWorker (WorkManager, upcoming delivery windows)
  RoutePilotApp.kt Application, Koin graph, periodic work
  MainActivity.kt  Compose host
```

Data flow: Room DAO -> RouteRepository -> ViewModel flows -> Compose UI.
Actions flow the other way: Compose -> ViewModel methods -> repository writes.
Route ordering is computed in the ViewModel from current origin and never written back automatically; manual reorder and pin operations persist the new order.
