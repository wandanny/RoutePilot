package com.local.routepilot.data

import android.content.Context
import androidx.room.Room
import com.local.routepilot.domain.FailureReason
import com.local.routepilot.domain.RouteInfo
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class BackupPayload(
  @Serializable(with = BackupRouteSerializer::class) val routes: List<RouteInfo>,
  val stops: List<Stop>,
)

private object BackupRouteSerializer : KSerializer<RouteInfo> {
  @Serializable
  private data class RouteData(val id: Long, val name: String, val date: String, val active: Boolean)

  private val delegate = RouteData.serializer()
  override val descriptor: SerialDescriptor = delegate.descriptor

  override fun serialize(encoder: Encoder, value: RouteInfo) {
    delegate.serialize(encoder, RouteData(value.id, value.name, value.date, value.active))
  }

  override fun deserialize(decoder: Decoder): RouteInfo {
    val data = delegate.deserialize(decoder)
    return RouteInfo(id = data.id, name = data.name, date = data.date, active = data.active)
  }
}

class RouteRepository(private val context: Context) {
  private val settings by lazy { AiSettings(context) }
  private val db = Room.databaseBuilder(context, AppDatabase::class.java, "routepilot.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
  private val dao = db.routeDao()
  private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

  val routes: Flow<List<RouteInfo>> = dao.routes().map { list -> list.map { it.toDomain() } }
  val activeRoute: Flow<RouteInfo?> = dao.activeRoute().map { it?.toDomain() }
  val failures: Flow<List<Stop>> = dao.failures().map { list -> list.map { it.toDomain() } }
  val allPending: Flow<List<Stop>> = dao.allPending().map { list -> list.map { it.toDomain() } }

  fun stops(routeId: Long): Flow<List<Stop>> = dao.stops(routeId).map { list -> list.map { it.toDomain() } }

  suspend fun stopsNow(routeId: Long): List<Stop> = dao.stops(routeId).first().map { it.toDomain() }

  suspend fun findByTrackingId(trackingId: String): Stop? =
    if (trackingId.isBlank()) null else dao.stopByTrackingId(trackingId)?.toDomain()

  suspend fun updateCoordinates(id: Long, latitude: Double, longitude: Double, estimated: Boolean = false) {
    dao.updateCoordinates(id, latitude, longitude, estimated)
  }

  suspend fun updateOrder(stops: List<Stop>) {
    dao.updateStops(stops.map { it.toEntity() })
  }

  suspend fun setRouteLocked(routeId: Long, locked: Boolean) {
    dao.setLocked(routeId, locked)
  }

  suspend fun cachedPoint(key: String): Pair<Double, Double>? =
    dao.cache(key)?.let { it.latitude to it.longitude }

  suspend fun saveCache(key: String, lat: Double, lon: Double, source: String) {
    dao.insertCache(
      GeocodeCacheEntity(
        key = key,
        latitude = lat,
        longitude = lon,
        source = source,
        createdAt = System.currentTimeMillis(),
      ),
    )
  }

  suspend fun clearGeocodeCache() {
    dao.clearCache()
  }

  suspend fun clearEstimatedCoordinates() {
    if (settings.aiCoordsCleared()) return
    dao.clearEstimatedCoordinates()
    settings.markAiCoordsCleared()
  }

  suspend fun ensureActiveRoute() {
    if (dao.activeRoute().first() == null) newRoute("Today's route")
  }

  suspend fun newRoute(name: String): Long {
    dao.archiveAllRoutes()
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return dao.insertRoute(RouteEntity(name = name, date = date, active = true))
  }

  suspend fun importCsv(text: String): Pair<Int, List<String>> {
    val parsed = CsvParser.parse(text)
    val stopFields = parsed.headers.filter {
      it in setOf("address", "label", "recipient", "phone", "notes", "priority", "windowstart", "windowend", "latitude", "lat", "longitude", "lon", "lng")
    }
    if ("address" !in stopFields) return 0 to listOf("No 'address' column found")

    ensureActiveRoute()
    val routeId = dao.activeRoute().first()?.id ?: return 0 to listOf("No active route")
    var index = (dao.maxOrderIndex(routeId) ?: -1) + 1
    val entities = parsed.rows.mapNotNull { row ->
      val address = CsvParser.field(row, "address")
      if (address.isBlank()) {
        null
      } else {
        StopEntity(
          routeId = routeId,
          address = address,
          label = CsvParser.field(row, "label", "name"),
          recipient = CsvParser.field(row, "recipient", "customer"),
          phone = CsvParser.field(row, "phone"),
          notes = CsvParser.field(row, "notes"),
          priority = CsvParser.field(row, "priority").toIntOrNull() ?: 0,
          windowStart = CsvParser.field(row, "windowstart", "window_start", "start"),
          windowEnd = CsvParser.field(row, "windowend", "window_end", "end"),
          latitude = CsvParser.field(row, "latitude", "lat").toDoubleOrNull(),
          longitude = CsvParser.field(row, "longitude", "lon", "lng").toDoubleOrNull(),
          orderIndex = index++,
          pinned = false,
          status = StopStatus.PENDING,
          failureReason = null,
          failureNote = "",
          retryAt = "",
        )
      }
    }
    dao.insertStops(entities)
    return entities.size to parsed.errors
  }

  suspend fun addStop(stop: Stop) {
    dao.insertStop(stop.toEntity())
  }

  suspend fun updateStops(stops: List<Stop>) {
    dao.updateStops(stops.map { it.toEntity() })
  }

  suspend fun setStatus(id: Long, status: StopStatus, reason: FailureReason? = null, note: String = "", retryAt: String = "") {
    val entity = dao.stop(id) ?: return
    dao.updateStops(
      listOf(
        entity.copy(
          status = status,
          failureReason = if (status == StopStatus.FAILED) reason else null,
          failureNote = if (status == StopStatus.FAILED) note else "",
          retryAt = if (status == StopStatus.FAILED) retryAt else "",
        ),
      ),
    )
  }

  suspend fun pin(id: Long) {
    val all = dao.activeRoute().first()?.let { dao.stops(it.id).first() } ?: return
    val target = all.firstOrNull { it.id == id } ?: return
    dao.updateStops(all.map { it.copy(pinned = it.id == id && !target.pinned) })
  }

  suspend fun exportBackup(): String {
    val routeList = dao.routes().first().map { it.toDomain() }
    val stopList = dao.routes().first().flatMap { route -> dao.stops(route.id).first().map { it.toDomain() } }
    return json.encodeToString(BackupPayload(routeList, stopList))
  }

  suspend fun importBackup(text: String) {
    val payload = json.decodeFromString<BackupPayload>(text)
    dao.clearAllStops()
    dao.clearAllRoutes()
    payload.routes.forEach { route ->
      val id = dao.insertRoute(
        RouteEntity(id = route.id, name = route.name, date = route.date, active = route.active, locked = route.locked),
      )
      val stops = payload.stops.filter { it.routeId == route.id }.map { it.copy(routeId = id).toEntity() }
      dao.insertStops(stops)
    }
  }

  suspend fun exportCsv(routeId: Long): String {
    val stops = dao.stops(routeId).first()
    val header = "address,label,recipient,phone,notes,priority,windowStart,windowEnd,latitude,longitude,status,failureReason,failureNote,retryAt"
    val lines = stops.map { stop ->
      listOf(
        stop.address, stop.label, stop.recipient, stop.phone, stop.notes,
        stop.priority.toString(), stop.windowStart, stop.windowEnd,
        stop.latitude?.toString().orEmpty(), stop.longitude?.toString().orEmpty(),
        stop.status.name, stop.failureReason?.name.orEmpty(), stop.failureNote, stop.retryAt,
      ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
    }
    return (listOf(header) + lines).joinToString("\n")
  }
}

private fun RouteEntity.toDomain() = RouteInfo(id, name, date, active, locked)

private fun StopEntity.toDomain() = Stop(
  id = id, routeId = routeId, address = address, label = label, recipient = recipient,
  phone = phone, notes = notes, priority = priority, windowStart = windowStart,
  windowEnd = windowEnd, latitude = latitude, longitude = longitude, orderIndex = orderIndex,
  pinned = pinned, status = status, failureReason = failureReason, failureNote = failureNote,
  retryAt = retryAt, trackingId = trackingId, estimatedCoordinates = estimatedCoordinates,
)

private fun Stop.toEntity() = StopEntity(
  id = id, routeId = routeId, address = address, label = label, recipient = recipient,
  phone = phone, notes = notes, priority = priority, windowStart = windowStart,
  windowEnd = windowEnd, latitude = latitude, longitude = longitude, orderIndex = orderIndex,
  pinned = pinned, status = status, failureReason = failureReason, failureNote = failureNote,
  retryAt = retryAt, trackingId = trackingId, estimatedCoordinates = estimatedCoordinates,
)
