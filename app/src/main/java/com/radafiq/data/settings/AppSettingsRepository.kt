package com.radafiq.data.settings

import android.content.Context
import com.radafiq.data.auth.LocalIdentityRepository
import com.radafiq.data.models.IndianAccountCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(
    val label: String
) {
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return values().firstOrNull { it.name == value } ?: DARK
        }
    }
}

data class AppSettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val selectedAccountIds: Set<String> = IndianAccountCatalog.defaultSelectedAccountIds(),
    val knownAccountIds: Set<String> = IndianAccountCatalog.defaultSelectedAccountIds(),
    val lastDriveBackupTime: String? = null,
    val lastDriveRestoreTime: String? = null
)

class AppSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    /** Call after a user switch so timestamps reload for the new account. */
    fun reloadForCurrentUser() {
        _settings.value = loadSettings()
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        persist(_settings.value.copy(themeMode = themeMode))
    }

    fun setAccountSelected(
        accountId: String,
        isSelected: Boolean
    ) {
        val updatedIds = _settings.value.selectedAccountIds.toMutableSet()

        if (isSelected) {
            updatedIds.add(accountId)
        } else if (updatedIds.size > 1) {
            updatedIds.remove(accountId)
        }

        persist(
            _settings.value.copy(
                selectedAccountIds = IndianAccountCatalog.sanitizeSelectedAccountIds(updatedIds)
            )
        )
    }

    fun setLastDriveBackupTime(timestamp: String?) {
        persist(_settings.value.copy(lastDriveBackupTime = timestamp))
    }

    fun setLastDriveRestoreTime(timestamp: String?) {
        persist(_settings.value.copy(lastDriveRestoreTime = timestamp))
    }

    fun exportSettings(): Map<String, Any> {
        return mutableMapOf<String, Any>(
            KEY_THEME_MODE to _settings.value.themeMode.name,
            KEY_SELECTED_ACCOUNT_IDS to _settings.value.selectedAccountIds.toList(),
            KEY_KNOWN_ACCOUNT_IDS to _settings.value.knownAccountIds.toList()
            // Drive timestamps are device-local and intentionally excluded from backup
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun restoreSettings(data: Map<String, Any?>) {
        val themeMode = AppThemeMode.fromStorage(data[KEY_THEME_MODE] as? String)
        val selectedAccountIds = when (val rawIds = data[KEY_SELECTED_ACCOUNT_IDS]) {
            is List<*> -> rawIds.filterIsInstance<String>().toSet()
            else -> emptySet()
        }
        val knownAccountIds = when (val rawIds = data[KEY_KNOWN_ACCOUNT_IDS]) {
            is List<*> -> rawIds.filterIsInstance<String>().toSet()
            else -> emptySet()
        }

        // Preserve existing local timestamps — they are not part of the backup payload
        if (selectedAccountIds.isEmpty()) {
            persist(
                AppSettingsState(
                    themeMode = themeMode,
                    lastDriveBackupTime = _settings.value.lastDriveBackupTime,
                    lastDriveRestoreTime = _settings.value.lastDriveRestoreTime
                )
            )
        } else {
            val merged = IndianAccountCatalog.mergeNewAccountIds(selectedAccountIds, knownAccountIds)
            persist(
                AppSettingsState(
                    themeMode = themeMode,
                    selectedAccountIds = merged.selectedAccountIds,
                    knownAccountIds = merged.knownAccountIds,
                    lastDriveBackupTime = _settings.value.lastDriveBackupTime,
                    lastDriveRestoreTime = _settings.value.lastDriveRestoreTime
                )
            )
        }
    }

    private fun userSuffix(): String {
        // Append userId so each account gets its own timestamp keys
        return "_${LocalIdentityRepository.userId()}"
    }

    private fun loadSettings(): AppSettingsState {
        val storedThemeMode = AppThemeMode.fromStorage(
            preferences.getString(KEY_THEME_MODE, null)
        )
        val storedAccountIds = preferences.getStringSet(KEY_SELECTED_ACCOUNT_IDS, null)
            ?.toSet()
            .orEmpty()
        val storedKnownIds = preferences.getStringSet(KEY_KNOWN_ACCOUNT_IDS, null)
            ?.toSet()
            .orEmpty()
        val suffix = userSuffix()

        if (storedAccountIds.isEmpty()) {
            return AppSettingsState(
                themeMode = storedThemeMode,
                lastDriveBackupTime = preferences.getString(KEY_LAST_DRIVE_BACKUP_TIME + suffix, null),
                lastDriveRestoreTime = preferences.getString(KEY_LAST_DRIVE_RESTORE_TIME + suffix, null)
            )
        }

        val merged = IndianAccountCatalog.mergeNewAccountIds(storedAccountIds, storedKnownIds)
        return AppSettingsState(
            themeMode = storedThemeMode,
            selectedAccountIds = merged.selectedAccountIds,
            knownAccountIds = merged.knownAccountIds,
            lastDriveBackupTime = preferences.getString(KEY_LAST_DRIVE_BACKUP_TIME + suffix, null),
            lastDriveRestoreTime = preferences.getString(KEY_LAST_DRIVE_RESTORE_TIME + suffix, null)
        )
    }

    private fun persist(settings: AppSettingsState) {
        val suffix = userSuffix()
        preferences.edit().apply {
            putString(KEY_THEME_MODE, settings.themeMode.name)
            putStringSet(KEY_SELECTED_ACCOUNT_IDS, settings.selectedAccountIds.toSet())
            putStringSet(KEY_KNOWN_ACCOUNT_IDS, settings.knownAccountIds.toSet())
            putString(KEY_LAST_DRIVE_BACKUP_TIME + suffix, settings.lastDriveBackupTime)
            putString(KEY_LAST_DRIVE_RESTORE_TIME + suffix, settings.lastDriveRestoreTime)
            apply()
        }
        _settings.value = settings
    }

    private companion object {
        const val PREFERENCES_NAME = "radafiq_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SELECTED_ACCOUNT_IDS = "selected_account_ids"
        const val KEY_KNOWN_ACCOUNT_IDS = "known_account_ids"
        const val KEY_LAST_DRIVE_BACKUP_TIME = "last_drive_backup_time"
        const val KEY_LAST_DRIVE_RESTORE_TIME = "last_drive_restore_time"
    }
}
