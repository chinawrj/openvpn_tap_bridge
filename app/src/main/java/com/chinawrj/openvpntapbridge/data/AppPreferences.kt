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
        
        private const val KEY_AP_INTERFACES = "ap_interfaces"
        private const val DEFAULT_AP_INTERFACES = "wlan0"
        
        private const val KEY_NCM_INTERFACE = "ncm_interface"
        private const val DEFAULT_NCM_INTERFACE = "ncm0"
        
        private const val KEY_OVPN_CONFIG_PATH = "ovpn_config_path"
        private const val DEFAULT_OVPN_CONFIG_PATH = ""  // Empty means not configured

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
    
    /**
     * AP interface fallback list (space-separated)
     */
    var apInterfaces: String
        get() = prefs.getString(KEY_AP_INTERFACES, DEFAULT_AP_INTERFACES) ?: DEFAULT_AP_INTERFACES
        set(value) = prefs.edit().putString(KEY_AP_INTERFACES, value).apply()
    
    /**
     * NCM/USB tethering interface name
     */
    var ncmInterface: String
        get() = prefs.getString(KEY_NCM_INTERFACE, DEFAULT_NCM_INTERFACE) ?: DEFAULT_NCM_INTERFACE
        set(value) = prefs.edit().putString(KEY_NCM_INTERFACE, value).apply()
    
    /**
     * OpenVPN config file path (stored in app's private directory)
     */
    var ovpnConfigPath: String
        get() = prefs.getString(KEY_OVPN_CONFIG_PATH, DEFAULT_OVPN_CONFIG_PATH) ?: DEFAULT_OVPN_CONFIG_PATH
        set(value) = prefs.edit().putString(KEY_OVPN_CONFIG_PATH, value).apply()
    
    /**
     * Get the default OVPN config path in app's private directory
     */
    fun getDefaultOvpnPath(context: Context): String {
        return "${context.filesDir.absolutePath}/client.ovpn"
    }
}
