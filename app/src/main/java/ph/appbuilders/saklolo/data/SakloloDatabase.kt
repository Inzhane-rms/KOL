package ph.appbuilders.saklolo.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import ph.appbuilders.saklolo.model.Alert
import ph.appbuilders.saklolo.model.AlertFile
import ph.appbuilders.saklolo.model.AlertPersistence
import ph.appbuilders.saklolo.triage.Urgency

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val transcript: String,
    val summary: String,
    val urgency: String,
    val createdAtMillis: Long,
    val lat: Double?,
    val lon: Double?,
    val hops: Int,
    val language: String,
    val summarySource: String,
    val audioPath: String?,
    val localOrigin: Boolean,
    val deliveredCount: Int,
)

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts")
    fun all(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rows: List<AlertEntity>)

    @Query("DELETE FROM alerts")
    fun deleteAll()

    @Transaction
    fun replaceAll(rows: List<AlertEntity>) {
        deleteAll()
        if (rows.isNotEmpty()) insertAll(rows)
    }
}

@Database(entities = [AlertEntity::class], version = 1, exportSchema = false)
abstract class SakloloDatabase : RoomDatabase() {
    abstract fun alerts(): AlertDao
}

class RoomAlertPersistence(private val database: SakloloDatabase) : AlertPersistence {
    override fun load(): AlertFile {
        val rows = database.alerts().all()
        val alerts = rows.map { it.toAlert() }
        val origins = rows.filter { it.localOrigin }.map { it.id }.toSet()
        return AlertFile(alerts, origins.toList())
    }

    override fun save(alerts: List<Alert>, localOriginIds: Set<String>) {
        database.alerts().replaceAll(alerts.map { it.toEntity(it.id in localOriginIds) })
    }
}

private fun AlertEntity.toAlert() = Alert(
    id = id,
    transcript = transcript,
    summary = summary,
    urgency = runCatching { Urgency.valueOf(urgency) }.getOrDefault(Urgency.NEEDS_HELP),
    createdAtMillis = createdAtMillis,
    lat = lat,
    lon = lon,
    hops = hops,
    language = language,
    summarySource = summarySource,
    audioPath = audioPath,
    deliveredCount = deliveredCount,
)

private fun Alert.toEntity(localOrigin: Boolean) = AlertEntity(
    id = id,
    transcript = transcript,
    summary = summary,
    urgency = urgency.name,
    createdAtMillis = createdAtMillis,
    lat = lat,
    lon = lon,
    hops = hops,
    language = language,
    summarySource = summarySource,
    audioPath = audioPath,
    localOrigin = localOrigin,
    deliveredCount = deliveredCount,
)
