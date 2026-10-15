package com.azza.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One saved answer from apps/api, kept so a screen has something to show
 * offline: the `data` of the envelope exactly as it arrived (JSON), not a
 * second schema of every model. [personal] rows belong to the signed-in
 * account and are deleted on sign-out.
 */
@Entity(tableName = "cached_responses")
data class CachedResponse(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
    val personal: Boolean,
)

@Dao
interface CachedResponseDao {
    @Query("SELECT * FROM cached_responses WHERE `key` = :key")
    suspend fun get(key: String): CachedResponse?

    @Upsert
    suspend fun put(row: CachedResponse)

    @Query("DELETE FROM cached_responses WHERE personal = 1")
    suspend fun deletePersonal()

    /** Keeps the newest [keep] rows whose key starts with [prefix] (e.g. opened listings). */
    @Query(
        "DELETE FROM cached_responses WHERE `key` LIKE :prefix || '%' AND `key` NOT IN " +
            "(SELECT `key` FROM cached_responses WHERE `key` LIKE :prefix || '%' ORDER BY savedAt DESC LIMIT :keep)",
    )
    suspend fun trim(prefix: String, keep: Int)
}

// Version 1. A cache, not a record: a schema change may simply drop it
// (fallbackToDestructiveMigration), so no migrations are kept.
@Database(entities = [CachedResponse::class], version = 1, exportSchema = false)
abstract class AzzaDatabase : RoomDatabase() {
    abstract fun cachedResponses(): CachedResponseDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AzzaDatabase =
        Room.databaseBuilder(context, AzzaDatabase::class.java, "azza.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun cachedResponses(db: AzzaDatabase): CachedResponseDao = db.cachedResponses()
}
