package com.example.pushapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pushapp.data.*
import com.example.pushapp.manager.AppLockManager
import com.example.pushapp.utils.AppLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.pushapp.service.AppLockService

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val appLockManager = AppLockManager(application)
    
    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()
    
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()
    
    private val _appLockSettings = MutableStateFlow<List<AppLockSettings>>(emptyList())
    val appLockSettings: StateFlow<List<AppLockSettings>> = _appLockSettings.asStateFlow()
    
    private val _pushUpSettings = MutableStateFlow<PushUpSettings?>(null)
    val pushUpSettings: StateFlow<PushUpSettings?> = _pushUpSettings.asStateFlow()
    
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    init {
        viewModelScope.launch {
            initializeApp()
        }
        
        // Collect monitoring state changes and update UI state
        viewModelScope.launch {
            isMonitoring.collect { monitoring ->
                _uiState.value = _uiState.value.copy(isMonitoring = monitoring)
            }
        }
        
        // Collect app lock settings from database
        viewModelScope.launch {
            appLockManager.getAllAppLockSettings().collect { settings ->
                AppLogger.d("AppLockViewModel", "Received ${settings.size} app lock settings from database")
                _appLockSettings.value = settings
                updateUiState()
            }
        }
    }
    
    private suspend fun initializeApp() {
        try {
            AppLogger.i("AppLockViewModel", "Initializing app...")
            
            // Initialize default settings
            appLockManager.initializeDefaultSettings()
            AppLogger.d("AppLockViewModel", "Default settings initialized")
            
            // Load installed apps
            val apps = appLockManager.getInstalledApps()
            _installedApps.value = apps
            AppLogger.i("AppLockViewModel", "Loaded ${apps.size} installed apps")
            
            // Check if service is already running
            _isMonitoring.value = AppLockService.isServiceRunning(getApplication())
            AppLogger.d("AppLockViewModel", "Service monitoring state: ${_isMonitoring.value}")
            
            // Start monitoring automatically if not already running
            if (!_isMonitoring.value) {
                try {
                    appLockManager.startMonitoring()
                    _isMonitoring.value = true
                    AppLogger.i("AppLockViewModel", "Auto-started monitoring service")
                } catch (e: Exception) {
                    AppLogger.e("AppLockViewModel", "Failed to auto-start monitoring", e)
                }
            }
            
        } catch (e: Exception) {
            // Handle initialization errors
            AppLogger.e("AppLockViewModel", "Failed to initialize app", e)
            _uiState.value = _uiState.value.copy(
                error = "Failed to initialize app: ${e.message}"
            )
        }
    }
    
    fun toggleAppLock(app: AppInfo, isLocked: Boolean) {
        viewModelScope.launch {
            try {
                AppLogger.i("AppLockViewModel", "Toggling app lock for ${app.appName} to $isLocked")
                
                val existingSettings = _appLockSettings.value.find { it.packageName == app.packageName }
                AppLogger.d("AppLockViewModel", "Existing settings found: ${existingSettings != null}")
                
                if (isLocked) {
                    val newSettings = AppLockSettings(
                        packageName = app.packageName,
                        appName = app.appName,
                        isLocked = true,
                        dailyTimeLimit = 60, // Default 1 hour
                        pushUpRequirement = 10, // Default 10 push-ups
                        timeUsedToday = 0
                    )
                    appLockManager.saveAppLockSettings(newSettings)
                    AppLogger.i("AppLockViewModel", "Saved new app lock settings for ${app.appName}")
                    
                    // Update local state
                    val currentList = _appLockSettings.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.packageName == app.packageName }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = newSettings
                    } else {
                        currentList.add(newSettings)
                    }
                    _appLockSettings.value = currentList
                    AppLogger.d("AppLockViewModel", "Updated local state, total settings: ${currentList.size}")
                } else {
                    existingSettings?.let { settings ->
                        val updatedSettings = settings.copy(isLocked = false)
                        appLockManager.updateAppLockSettings(updatedSettings)
                        AppLogger.i("AppLockViewModel", "Updated app lock settings for ${app.appName} to unlocked")
                        
                        // Update local state
                        val currentList = _appLockSettings.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.packageName == app.packageName }
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = updatedSettings
                            _appLockSettings.value = currentList
                        }
                    }
                }
                
                // Update UI state
                updateUiState()
                
            } catch (e: Exception) {
                AppLogger.e("AppLockViewModel", "Failed to update app lock for ${app.appName}", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update app lock: ${e.message}"
                )
            }
        }
    }
    
    fun updateTimeLimit(packageName: String, timeLimit: Int) {
        viewModelScope.launch {
            try {
                val settings = _appLockSettings.value.find { it.packageName == packageName }
                settings?.let { existingSettings ->
                    val updatedSettings = existingSettings.copy(dailyTimeLimit = timeLimit)
                    appLockManager.updateAppLockSettings(updatedSettings)
                    
                    // Update local state
                    val currentList = _appLockSettings.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.packageName == packageName }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = updatedSettings
                        _appLockSettings.value = currentList
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update time limit: ${e.message}"
                )
            }
        }
    }
    
    fun updatePushUpRequirement(packageName: String, pushUpCount: Int) {
        viewModelScope.launch {
            try {
                val settings = _appLockSettings.value.find { it.packageName == packageName }
                settings?.let { existingSettings ->
                    val updatedSettings = existingSettings.copy(pushUpRequirement = pushUpCount)
                    appLockManager.updateAppLockSettings(updatedSettings)
                    
                    // Update local state
                    val currentList = _appLockSettings.value.toMutableList()
                    val existingIndex = currentList.indexOfFirst { it.packageName == packageName }
                    if (existingIndex >= 0) {
                        currentList[existingIndex] = updatedSettings
                        _appLockSettings.value = currentList
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update push-up requirement: ${e.message}"
                )
            }
        }
    }
    
    fun unlockAppWithPushUps(app: AppLockSettings, pushUpCount: Int) {
        viewModelScope.launch {
            try {
                val success = appLockManager.verifyPushUpsAndUnlock(app.packageName, pushUpCount)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        message = "App unlocked successfully! Great job on the push-ups!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Not enough push-ups completed. You need ${app.pushUpRequirement} push-ups to unlock ${app.appName}."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to unlock app: ${e.message}"
                )
            }
        }
    }
    
    fun startMonitoring() {
        viewModelScope.launch {
            try {
                appLockManager.startMonitoring()
                _isMonitoring.value = true
                _uiState.value = _uiState.value.copy(
                    message = "App monitoring started successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start monitoring: ${e.message}"
                )
            }
        }
    }
    
    fun stopMonitoring() {
        viewModelScope.launch {
            try {
                appLockManager.stopMonitoring()
                _isMonitoring.value = false
                _uiState.value = _uiState.value.copy(
                    message = "App monitoring stopped"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to stop monitoring: ${e.message}"
                )
            }
        }
    }
    
    fun updatePushUpSettings(settings: PushUpSettings) {
        viewModelScope.launch {
            try {
                appLockManager.updatePushUpSettings(settings)
                _pushUpSettings.value = settings
                _uiState.value = _uiState.value.copy(
                    message = "Push-up settings updated successfully!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update push-up settings: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
    
    fun clearMonitoringState() {
        _isMonitoring.value = false
    }
    
    fun checkServiceStatus() {
        _isMonitoring.value = AppLockService.isServiceRunning(getApplication())
    }
    
    private fun updateUiState() {
        val totalApps = _installedApps.value.size
        val lockedApps = _appLockSettings.value.count { it.isLocked }
        
        AppLogger.d("AppLockViewModel", "Updating UI state: totalApps=$totalApps, lockedApps=$lockedApps")
        
        _uiState.value = _uiState.value.copy(
            totalApps = totalApps,
            lockedApps = lockedApps
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        appLockManager.stopMonitoring()
    }
}

data class AppLockUiState(
    val totalApps: Int = 0,
    val lockedApps: Int = 0,
    val error: String? = null,
    val message: String? = null,
    val isLoading: Boolean = false,
    val isMonitoring: Boolean = false
)
