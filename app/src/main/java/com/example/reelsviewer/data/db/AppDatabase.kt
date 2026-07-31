package com.example.reelsviewer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * SQLCipher-encrypted Room database ensuring zero unencrypted metadata leakage.
 */
@Database(entities = [LikedReelEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun likedReelDao(): LikedReelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, passphrase: CharArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                System.loadLibrary("sqlcipher")
                val passphraseBytes = SQLiteDatabase.getBytes(passphrase)
                val factory = SupportFactory(passphraseBytes)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reels_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
