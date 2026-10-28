package com.markettwits.devx.tgsignin.data.datasource

import android.content.Context
import java.util.UUID

fun interface DeviceIdProvider {
    fun getDeviceId(): String
}

class SharedPreferencesDeviceIdProvider(
    private val context: Context
) : DeviceIdProvider {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getDeviceId(): String {
        synchronized(this) {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) {
                return existing
            }
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            return newId
        }
    }

    private companion object {
        const val PREFS_NAME = "bloom_device_identity"
        const val KEY_DEVICE_ID = "device_id"
    }
}
