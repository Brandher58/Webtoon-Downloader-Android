package com.brandher.webtoondl.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chapters ADD COLUMN output_format TEXT NOT NULL DEFAULT 'IMAGES'")
    }
}

/**
 * 2 → 3: la unicidad de (source_id, episode_no) era GLOBAL y al sincronizar dos series con los
 * mismos números se borraban capítulos entre sí (REPLACE). Se hace única por serie.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM chapters WHERE rowid NOT IN " +
            "(SELECT MIN(rowid) FROM chapters GROUP BY series_id, source_id, episode_no)")
        db.execSQL("DROP INDEX IF EXISTS index_chapters_source_id_episode_no")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_chapters_series_id_source_id_episode_no " +
                "ON chapters (series_id, source_id, episode_no)",
        )
    }
}