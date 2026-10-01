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
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `events` (
                    `eventId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `eventName` TEXT, 
                    `eventDate` TEXT, 
                    `venue` TEXT
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO `events` (`eventName`, `eventDate`, `venue`) 
                SELECT DISTINCT `eventName`, `eventDate`, `venue` 
                FROM `ticket_details`
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `ticket_details_new` (
                    `documentId` INTEGER NOT NULL, 
                    `eventId` INTEGER NOT NULL, 
                    `seat` TEXT, 
                    PRIMARY KEY(`documentId`), 
                    FOREIGN KEY(`documentId`) REFERENCES `documents`(`documentId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`eventId`) REFERENCES `events`(`eventId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())


            db.execSQL("""
                INSERT INTO `ticket_details_new` (`documentId`, `eventId`, `seat`)
                SELECT t.`documentId`, e.`eventId`, t.`seat`
                FROM `ticket_details` t
                INNER JOIN `events` e ON 
                    COALESCE(t.`eventName`, '') = COALESCE(e.`eventName`, '') AND 
                    COALESCE(t.`eventDate`, '') = COALESCE(e.`eventDate`, '') AND 
                    COALESCE(t.`venue`, '') = COALESCE(e.`venue`, '')
            """.trimIndent())

            db.execSQL("DROP TABLE `ticket_details`")

            db.execSQL("ALTER TABLE `ticket_details_new` RENAME TO `ticket_details`")

            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ticket_details_documentId` ON `ticket_details` (`documentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ticket_details_eventId` ON `ticket_details` (`eventId`)")
        }
    }

    fun getDatabase(context: Context): AppDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "digitalwalletdb"
            )
                // Add MIGRATION_5_6 to the builder
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigrationFrom()
                .build()
                .also { INSTANCE = it }
        }
}
