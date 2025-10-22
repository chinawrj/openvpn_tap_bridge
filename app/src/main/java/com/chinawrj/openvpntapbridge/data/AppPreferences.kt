package com.chinawrj.openvpntapbridge.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Application configuration manager
 * Use SharedPreferences to store application configuration
 */
class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "openvpn_tap_bridge_prefs"
        private const val KEY_INTERFACE_NAME = "interface_name"
        private const val DEFAULT_INTERFACE = "tap0"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Get current monitored interface name
     */
    var interfaceName: String
        get() = prefs.getString(KEY_INTERFACE_NAME, DEFAULT_INTERFACE) ?: DEFAULT_INTERFACE
        set(value) = prefs.edit().putString(KEY_INTERFACE_NAME, value).apply()
}
