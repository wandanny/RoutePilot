package com.local.routepilot.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.local.routepilot.domain.FailureReason
import com.local.routepilot.domain.StopStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "routes")
data class RouteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val date: String,
  val active: Boolean,
  val locked: Boolean = false,
)

@Entity(
  tableName = "stops",
  foreignKeys = [ForeignKey(
    entity = RouteEntity::class,
    parentColumns = ["id"],
    childColumns = ["routeId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("routeId")],
)
data class StopEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val routeId: Long,
  val address: String,
  val label: String,
  val recipient: String,
  val phone: String,
  val notes: String,
  val priority: Int,
  val windowStart: String,
  val windowEnd: String,
  val latitude: Double?,
  val longitude: Double?,
  val orderIndex: Int,
  val pinned: Boolean,
  val status: StopStatus,
  val failureReason: FailureReason?,
  val failureNote: String,
  val retryAt: String,
  val trackingId: String = "",
  val estimatedCoordinates: Boolean = false,
)

@Entity(tableName = "geocode_cache")
data class GeocodeCacheEntity(
  @PrimaryKey val key: String,
  val latitude: Double,
  val longitude: Double,
  val source: String,
  val createdAt: Long,
)

class Converters {
  @TypeConverter fun status(value: String): StopStatus = StopStatus.valueOf(value)
  @TypeConverter fun statusName(value: StopStatus): String = value.name
  @TypeConverter fun reason(value: String?): FailureReason? = value?.let { FailureReason.valueOf(it) }
  @TypeConverter fun reasonName(value: FailureReason?): String? = value?.name
}

@Dao
interface RouteDao {
  @Query("SELECT * FROM routes ORDER BY id DESC")
  fun routes(): Flow<List<RouteEntity>>

  @Query("SELECT * FROM routes WHERE active = 1 ORDER BY id DESC LIMIT 1")
  fun activeRoute(): Flow<RouteEntity?>

  @Query("SELECT * FROM stops WHERE routeId = :routeId ORDER BY orderIndex, id")
  fun stops(routeId: Long): Flow<List<StopEntity>>

  @Query("SELECT * FROM stops WHERE status = 'FAILED' ORDER BY routeId DESC, id")
  fun failures(): Flow<List<StopEntity>>

  @Query("SELECT * FROM stops WHERE status = 'PENDING' ORDER BY orderIndex, id")
  fun allPending(): Flow<List<StopEntity>>

  @Query("SELECT * FROM stops WHERE id = :id")
  suspend fun stop(id: Long): StopEntity?

  @Query("SELECT * FROM stops WHERE trackingId = :trackingId AND trackingId != '' LIMIT 1")
  suspend fun stopByTrackingId(trackingId: String): StopEntity?

  @Query("SELECT MAX(orderIndex) FROM stops WHERE routeId = :routeId")
  suspend fun maxOrderIndex(routeId: Long): Int?

  @Query("UPDATE stops SET latitude = :latitude, longitude = :longitude, estimatedCoordinates = :estimated WHERE id = :id")
  suspend fun updateCoordinates(id: Long, latitude: Double, longitude: Double, estimated: Boolean)

  @Query("UPDATE stops SET latitude = NULL, longitude = NULL, estimatedCoordinates = 0 WHERE estimatedCoordinates = 1")
  suspend fun clearEstimatedCoordinates()

  @Query("SELECT * FROM geocode_cache WHERE key = :key")
  suspend fun cache(key: String): GeocodeCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCache(entry: GeocodeCacheEntity)

  @Query("DELETE FROM geocode_cache")
  suspend fun clearCache()

  @Query("UPDATE routes SET locked = :locked WHERE id = :routeId")
  suspend fun setLocked(routeId: Long, locked: Boolean)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertRoute(route: RouteEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStops(stops: List<StopEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertStop(stop: StopEntity): Long

  @Update suspend fun updateStops(stops: List<StopEntity>)

  @Query("UPDATE routes SET active = 0")
  suspend fun archiveAllRoutes()

  @Query("DELETE FROM stops WHERE routeId = :routeId")
  suspend fun clearStops(routeId: Long)

  @Query("DELETE FROM stops")
  suspend fun clearAllStops()

  @Query("DELETE FROM routes")
  suspend fun clearAllRoutes()
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
  override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE stops ADD COLUMN trackingId TEXT NOT NULL DEFAULT ''")
  }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
  override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE stops ADD COLUMN estimatedCoordinates INTEGER NOT NULL DEFAULT 0")
  }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
  override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE routes ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS geocode_cache (" +
        "key TEXT PRIMARY KEY NOT NULL, " +
        "latitude REAL NOT NULL, " +
        "longitude REAL NOT NULL, " +
        "source TEXT NOT NULL, " +
        "createdAt INTEGER NOT NULL" +
        ")",
    )
  }
}

@Database(
  entities = [RouteEntity::class, StopEntity::class, GeocodeCacheEntity::class],
  version = 4,
  exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun routeDao(): RouteDao
}
