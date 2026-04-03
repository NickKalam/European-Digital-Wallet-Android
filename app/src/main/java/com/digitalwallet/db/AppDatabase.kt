package com.digitalwallet.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.digitalwallet.dao.AuthLogDao
import com.digitalwallet.dao.DocumentDao
import com.digitalwallet.dao.UserDao
import com.digitalwallet.model.AuthLog
import com.digitalwallet.model.Converters
import com.digitalwallet.model.Document
import com.digitalwallet.model.DriverLicenseDetails
import com.digitalwallet.model.IdCardDetails
import com.digitalwallet.model.TicketDetails
import com.digitalwallet.model.User

@Database(
    entities = [
        User::class,
        Document::class,
        AuthLog::class,
        IdCardDetails::class,
        DriverLicenseDetails::class,
        TicketDetails::class
    ],
    version = 5
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun documentDao(): DocumentDao
    abstract fun authLogDao(): AuthLogDao
}
