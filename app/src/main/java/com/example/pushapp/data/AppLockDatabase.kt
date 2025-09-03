package com.example.pushapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_lock_settings")
    fun getAllAppLockSettings(): Flow<List<AppLockSettings>>
    
    @Query("SELECT * FROM app_lock_settings WHERE isLocked = 1")
    fun getLockedApps(): Flow<List<AppLockSettings>>
    
    @Query("SELECT * FROM app_lock_settings WHERE packageName = :packageName")
    suspend fun getAppLockSettings(packageName: String): AppLockSettings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppLockSettings(settings: AppLockSettings)
    
    @Update
    suspend fun updateAppLockSettings(settings: AppLockSettings)
    
    @Delete
    suspend fun deleteAppLockSettings(settings: AppLockSettings)
    
    @Query("UPDATE app_lock_settings SET timeUsedToday = :timeUsed WHERE packageName = :packageName")
    suspend fun updateTimeUsed(packageName: String, timeUsed: Int)
    
    @Query("UPDATE app_lock_settings SET lastResetDate = :resetDate, timeUsedToday = 0")
    suspend fun resetDailyUsage(resetDate: Long)
}

@Dao
interface PushUpSettingsDao {
    @Query("SELECT * FROM push_up_settings WHERE id = 1")
    fun getPushUpSettings(): Flow<PushUpSettings?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPushUpSettings(settings: PushUpSettings)
    
    @Update
    suspend fun updatePushUpSettings(settings: PushUpSettings)
}

@Database(
    entities = [AppLockSettings::class, PushUpSettings::class],
    version = 1,
    exportSchema = false
)
abstract class AppLockDatabase : RoomDatabase() {
    abstract fun appLockDao(): AppLockDao
    abstract fun pushUpSettingsDao(): PushUpSettingsDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppLockDatabase? = null
        
        fun getInstance(context: Context): AppLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppLockDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
        
        const val DATABASE_NAME = "app_lock_database"
    }
}
