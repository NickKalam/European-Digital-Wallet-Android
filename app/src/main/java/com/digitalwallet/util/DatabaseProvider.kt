package com.digitalwallet.util

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.digitalwallet.db.AppDatabase

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE id_card_details ADD COLUMN ownerPhotoUri TEXT")
            db.execSQL("ALTER TABLE driver_license_details ADD COLUMN ownerPhotoUri TEXT")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2,3)
    {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM users")
            db.execSQL("DELETE FROM documents")
        }
    }


    private val MIGRATION_3_4 = object : Migration(3,4)
    {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX index_id_card_details_idNumber ON id_card_details(idNumber)")
            db.execSQL("CREATE UNIQUE INDEX index_driver_license_details_licenseNumber ON driver_license_details(licenseNumber)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4,5)
    {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM users")
            db.execSQL("DELETE FROM documents")
            db.execSQL("DELETE FROM id_card_details")
            db.execSQL("DELETE FROM authlogs")
            db.execSQL("DROP TABLE IF EXISTS transactions")
        }
    }
    fun getDatabase(context: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "digitalwalletdb"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3,MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationFrom()
                .build()
                .also { INSTANCE = it }
        }
}
