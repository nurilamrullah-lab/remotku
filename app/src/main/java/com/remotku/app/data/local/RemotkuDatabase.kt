package com.remotku.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.remotku.app.domain.tv.DiscoverySource
import com.remotku.app.domain.tv.TvDevice
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tv_devices")
data class TvDeviceEntity(
    @PrimaryKey val host: String,
    val name: String,
    val model: String?,
    val atvPort: Int?,
    val adbPort: Int?,
    val isPaired: Boolean,
    val lastMode: String?,
    val lastConnectedAt: Long,
) {
    fun toDomain() = TvDevice(
        host = host,
        name = name,
        model = model,
        atvPort = atvPort,
        adbPort = adbPort,
        sources = setOf(DiscoverySource.SAVED),
        isPaired = isPaired,
        isOnline = false,
        isSaved = true,
    )
}

@Dao
interface TvDeviceDao {
    @Query("SELECT * FROM tv_devices ORDER BY lastConnectedAt DESC")
    fun observeAll(): Flow<List<TvDeviceEntity>>

    @Query("SELECT * FROM tv_devices WHERE host = :host")
    suspend fun find(host: String): TvDeviceEntity?

    @Upsert
    suspend fun upsert(entity: TvDeviceEntity)

    @Query("DELETE FROM tv_devices WHERE host = :host")
    suspend fun delete(host: String)
}

@Database(entities = [TvDeviceEntity::class], version = 1, exportSchema = true)
abstract class RemotkuDatabase : RoomDatabase() {
    abstract fun tvDeviceDao(): TvDeviceDao

    companion object {
        fun create(context: Context): RemotkuDatabase =
            Room.databaseBuilder(context.applicationContext, RemotkuDatabase::class.java, "remotku.db").build()
    }
}
