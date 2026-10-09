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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ph.appbuilders.saklolo.group.ConcertGroup
import ph.appbuilders.saklolo.group.GroupNote
import ph.appbuilders.saklolo.group.GroupPersistence
import ph.appbuilders.saklolo.group.GroupSnapshot
import ph.appbuilders.saklolo.group.Sighting
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
    val responding: Boolean,
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

@Entity(tableName = "concert_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val joinedAtMillis: Long,
    val active: Boolean,
    val createdHere: Boolean = false,
)

@Entity(tableName = "group_notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val sender: String,
    val body: String,
    val createdAtMillis: Long,
    val hops: Int,
    val audioPath: String?,
    val lat: Double?,
    val lon: Double?,
    val urgency: String?,
    val localOrigin: Boolean,
    val kind: String = "text",
)

@Entity(tableName = "last_seen")
data class SightingEntity(
    @PrimaryKey val sightingKey: String,
    val groupId: String,
    val name: String,
    val heardAtMillis: Long,
    val lat: Double?,
    val lon: Double?,
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM concert_groups")
    fun groups(): List<GroupEntity>

    @Query("SELECT * FROM group_notes")
    fun notes(): List<NoteEntity>

    @Query("SELECT * FROM last_seen")
    fun sightings(): List<SightingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGroups(rows: List<GroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotes(rows: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSightings(rows: List<SightingEntity>)

    @Query("DELETE FROM concert_groups")
    fun deleteGroups()

    @Query("DELETE FROM group_notes")
    fun deleteNotes()

    @Query("DELETE FROM last_seen")
    fun deleteSightings()

    @Transaction
    fun replaceAll(groups: List<GroupEntity>, notes: List<NoteEntity>, sightings: List<SightingEntity>) {
        deleteGroups()
        deleteNotes()
        deleteSightings()
        if (groups.isNotEmpty()) insertGroups(groups)
        if (notes.isNotEmpty()) insertNotes(notes)
        if (sightings.isNotEmpty()) insertSightings(sightings)
    }
}

@Database(
    entities = [AlertEntity::class, GroupEntity::class, NoteEntity::class, SightingEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class SakloloDatabase : RoomDatabase() {
    abstract fun alerts(): AlertDao
    abstract fun groups(): GroupDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alerts ADD COLUMN responding INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE concert_groups ADD COLUMN createdHere INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE group_notes ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS concert_groups (" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, joinedAtMillis INTEGER NOT NULL, active INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS group_notes (" +
                "id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, sender TEXT NOT NULL, body TEXT NOT NULL, " +
                "createdAtMillis INTEGER NOT NULL, hops INTEGER NOT NULL, audioPath TEXT, lat REAL, lon REAL, " +
                "urgency TEXT, localOrigin INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS last_seen (" +
                "sightingKey TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, name TEXT NOT NULL, " +
                "heardAtMillis INTEGER NOT NULL, lat REAL, lon REAL)",
        )
    }
}

class RoomGroupPersistence(private val database: SakloloDatabase) : GroupPersistence {
    override fun load(): GroupSnapshot {
        val dao = database.groups()
        val groups = dao.groups()
        val notes = dao.notes()
        return GroupSnapshot(
            groups = groups.map { ConcertGroup(it.id, it.name, it.joinedAtMillis, it.createdHere) },
            notes = notes.map { it.toNote() },
            sightings = dao.sightings().map { it.toSighting() },
            localOriginIds = notes.filter { it.localOrigin }.map { it.id },
            activeId = groups.firstOrNull { it.active }?.id,
        )
    }

    override fun save(snapshot: GroupSnapshot) {
        database.groups().replaceAll(
            snapshot.groups.map {
                GroupEntity(it.id, it.name, it.joinedAtMillis, it.id == snapshot.activeId, it.createdHere)
            },
            snapshot.notes.map { it.toEntity(it.id in snapshot.localOriginIds.toSet()) },
            snapshot.sightings.map {
                SightingEntity(
                    sightingKey = it.groupId + "\u0000" + it.name,
                    groupId = it.groupId,
                    name = it.name,
                    heardAtMillis = it.heardAtMillis,
                    lat = it.lat,
                    lon = it.lon,
                )
            },
        )
    }
}

private fun NoteEntity.toNote() = GroupNote(
    id = id,
    groupId = groupId,
    sender = sender,
    body = body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    audioPath = audioPath,
    lat = lat,
    lon = lon,
    urgency = urgency?.let { runCatching { Urgency.valueOf(it) }.getOrNull() },
    kind = kind,
)

private fun GroupNote.toEntity(localOrigin: Boolean) = NoteEntity(
    id = id,
    groupId = groupId,
    sender = sender,
    body = body,
    createdAtMillis = createdAtMillis,
    hops = hops,
    audioPath = audioPath,
    lat = lat,
    lon = lon,
    urgency = urgency?.name,
    localOrigin = localOrigin,
    kind = kind,
)

private fun SightingEntity.toSighting() = Sighting(groupId, name, heardAtMillis, lat, lon)

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
    responding = responding,
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
    responding = responding,
)
