package com.papiai.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.papiai.domain.model.HistoryEntry
import com.papiai.domain.model.HistoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "history_entries")
data class HistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val type: String,
  val title: String,
  val content: String,
  val secondaryContent: String?,
  val languageCode: String,
  val timestampMs: Long,
)

@Entity(tableName = "installed_models")
data class InstalledModelEntity(
  @PrimaryKey val modelId: String,
  val localDirPath: String,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val verifiedSha256: String?,
  val installedAtMs: Long,
)

@Dao
interface HistoryDao {
  @Query("SELECT * FROM history_entries ORDER BY timestampMs DESC LIMIT 100")
  fun observeAll(): Flow<List<HistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: HistoryEntity): Long

  @Query("DELETE FROM history_entries WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM history_entries")
  suspend fun clearAll()
}

@Dao
interface InstalledModelDao {
  @Query("SELECT * FROM installed_models")
  fun observeInstalled(): Flow<List<InstalledModelEntity>>

  @Query("SELECT * FROM installed_models WHERE modelId = :modelId LIMIT 1")
  suspend fun getById(modelId: String): InstalledModelEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: InstalledModelEntity)

  @Query("DELETE FROM installed_models WHERE modelId = :modelId")
  suspend fun deleteById(modelId: String)

  @Query("DELETE FROM installed_models")
  suspend fun clearAll()
}

@Database(
  entities = [HistoryEntity::class, InstalledModelEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class PapiDatabase : RoomDatabase() {
  abstract fun historyDao(): HistoryDao
  abstract fun installedModelDao(): InstalledModelDao

  companion object {
    @Volatile private var instance: PapiDatabase? = null

    fun getInstance(context: Context): PapiDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          PapiDatabase::class.java,
          "papi_ai_local.db",
        ).build().also { instance = it }
      }
    }
  }
}

object PapiDataProvider {
  fun createHistoryRepository(context: Context): HistoryRepository =
    HistoryRepository(PapiDatabase.getInstance(context).historyDao())

  fun createModelRepository(context: Context): com.papiai.data.models.ModelDownloadRepository =
    com.papiai.data.models.ModelDownloadRepository(
      context.applicationContext,
      PapiDatabase.getInstance(context).installedModelDao(),
    )
}

class HistoryRepository(private val dao: HistoryDao) {
  val entries: Flow<List<HistoryEntry>> = dao.observeAll().map { rows ->
    rows.map { row ->
      HistoryEntry(
        id = row.id,
        type = runCatching { HistoryType.valueOf(row.type) }.getOrDefault(HistoryType.OCR),
        title = row.title,
        content = row.content,
        secondaryContent = row.secondaryContent,
        languageCode = row.languageCode,
        timestampMs = row.timestampMs,
      )
    }
  }

  suspend fun save(entry: HistoryEntry) {
    dao.insert(
      HistoryEntity(
        id = entry.id,
        type = entry.type.name,
        title = entry.title,
        content = entry.content,
        secondaryContent = entry.secondaryContent,
        languageCode = entry.languageCode,
        timestampMs = entry.timestampMs,
      )
    )
  }

  suspend fun delete(id: Long) = dao.deleteById(id)

  suspend fun clearAll() = dao.clearAll()
}
