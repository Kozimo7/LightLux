package com.example.lightluxmeter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lightluxmeter.data.model.ExposureReading
import net.sqlcipher.database.SupportFactory

@Database(entities = [ExposureReading::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exposureDao(): ExposureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val PASSPHRASE = "LightLuxSecretKey".toByteArray()

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(PASSPHRASE)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "exposure_history.db"
                )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
