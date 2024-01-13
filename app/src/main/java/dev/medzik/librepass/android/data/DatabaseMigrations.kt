package dev.medzik.librepass.android.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Credentials
                db.execSQL("ALTER TABLE Credentials RENAME COLUMN biometricProtectedPrivateKey to biometricPrivateKey")
                db.execSQL("ALTER TABLE Credentials RENAME COLUMN biometricProtectedPrivateKeyIV to biometricPrivateKeyIV")

                // LocalCipher
                db.execSQL("ALTER TABLE CipherTable RENAME TO LocalCipher")
                db.execSQL("ALTER TABLE LocalCipher ADD COLUMN needUpload INTEGER NOT NULL DEFAULT 0")
            }
        }
}