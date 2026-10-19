package com.azza.core.database.pos

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The register's database (POS-03). Separate from [com.azza.core.database.AzzaDatabase]
 * on purpose: that one is a cache and is dropped on any schema change; this
 * one holds sales that have not reached the server yet and must survive
 * every app update. So: an exported schema (core/database/schemas), a
 * migration for every version step in [PosMigrations], and NO destructive
 * fallback — a missing migration crashes in testing instead of silently
 * deleting someone's takings.
 */
@Database(
    entities = [
        CatalogProductEntity::class,
        CatalogCodeEntity::class,
        BranchStockEntity::class,
        SyncStateEntity::class,
        PendingSaleEntity::class,
        PendingSaleItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun pos(): PosDao

    companion object {
        const val NAME = "azza_pos.db"

        fun build(context: Context): PosDatabase =
            Room.databaseBuilder(context, PosDatabase::class.java, NAME)
                .addMigrations(*PosMigrations.ALL)
                .build()
    }
}

/**
 * Every schema step of [PosDatabase], in order. Version 1 is the first, so
 * there is none yet; the next change adds `MIGRATION_1_2` here and a
 * migration test against `schemas/…/1.json`.
 */
object PosMigrations {
    val ALL: Array<Migration> = emptyArray()
}

@Module
@InstallIn(SingletonComponent::class)
object PosDatabaseModule {
    @Provides
    @Singleton
    fun posDatabase(@ApplicationContext context: Context): PosDatabase = PosDatabase.build(context)

    @Provides
    fun posDao(db: PosDatabase): PosDao = db.pos()
}
