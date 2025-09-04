package com.example.pushapp.data

import android.content.Context
import android.content.Intent

data class PermissionInfo(
    val permission: String,
    val displayName: String,
    val description: String,
    val isRequired: Boolean,
    val isGranted: Boolean,
    val settingsIntent: Intent? = null,
    val icon: Int? = null
)

data class PermissionState(
    val allPermissionsGranted: Boolean,
    val requiredPermissions: List<PermissionInfo>,
    val optionalPermissions: List<PermissionInfo>,
    val missingRequiredCount: Int,
    val missingOptionalCount: Int
)
