package com.chinawrj.openvpntapbridge.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Script installation and management
 * Manages vpn-hotspot-bridge.sh script installation and execution
 */
object ScriptManager {
    private const val TAG = "ScriptManager"
    
    // Script file name in assets
    private const val SCRIPT_ASSET = "vpn-hotspot-bridge.sh"
    
    // Installation locations (prefer service.d for auto-start on boot)
    const val INSTALL_PATH_SERVICE = "/data/adb/service.d/vpn-hotspot-bridge.sh"
    const val INSTALL_PATH_DATA = "/data/local/tmp/vpn-hotspot-bridge.sh"
    
    // Log file location
    const val LOG_PATH = "/data/local/tmp/vpn-bridge.log"
    
    /**
     * Script installation result
     */
    data class InstallResult(
        val success: Boolean,
        val message: String,
        val installedPath: String? = null
    )
    
    /**
     * Script status information
     */
    data class ScriptStatus(
        val installed: Boolean,
        val installedPath: String? = null,
        val running: Boolean = false,
        val executable: Boolean = false
    )
    
    /**
     * Check if script is installed
     * @param preferredPath Preferred installation path (default: /data/adb/service.d)
     * @return Script status
     */
    fun checkStatus(preferredPath: String = INSTALL_PATH_SERVICE): ScriptStatus {
        val paths = listOf(preferredPath, INSTALL_PATH_SERVICE, INSTALL_PATH_DATA)
        
        for (path in paths) {
            if (FileReaders.exists(path)) {
                val isExecutable = checkExecutable(path)
                val isRunning = checkRunning()
                
                Log.d(TAG, "Script found at $path, executable=$isExecutable, running=$isRunning")
                
                return ScriptStatus(
                    installed = true,
                    installedPath = path,
                    running = isRunning,
                    executable = isExecutable
                )
            }
        }
        
        Log.d(TAG, "Script not installed")
        return ScriptStatus(installed = false)
    }
    
    /**
     * Install script from assets to target path
     * @param context Application context
     * @param targetPath Target installation path (defaults to /data/adb/service.d for auto-start)
     * @param apInterfaces AP interface fallback list (space-separated)
     * @param ncmInterface NCM/USB tethering interface name
     * @param ovpnConfigPath OpenVPN config file path
     * @return Installation result
     */
    fun install(
        context: Context, 
        targetPath: String = INSTALL_PATH_SERVICE,
        apInterfaces: String = "ap0 wlan1 softap0 swlan0 wlan0",
        ncmInterface: String = "ncm0",
        ovpnConfigPath: String = ""
    ): InstallResult {
        try {
            // Check if already installed
            if (FileReaders.exists(targetPath)) {
                Log.d(TAG, "Script already exists at $targetPath")
                return InstallResult(
                    success = false,
                    message = "Script already installed at $targetPath"
                )
            }
            
            // Determine OVPN config path to use
            val finalOvpnPath = if (ovpnConfigPath.isNotEmpty()) {
                ovpnConfigPath
            } else {
                "/data/adb/service.d/pixel8a.ovpn"  // Default fallback
            }
            
            // Read script from assets
            var scriptContent = context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
            
            // Replace placeholders with user-configured values
            scriptContent = scriptContent
                .replace("{{AP_INTERFACES}}", apInterfaces.trim())
                .replace("{{NCM_INTERFACE}}", ncmInterface.trim())
                .replace("{{OVPN_CONFIG_PATH}}", finalOvpnPath)
            
            Log.d(TAG, "Replaced AP_INTERFACES with: $apInterfaces")
            Log.d(TAG, "Replaced NCM_INTERFACE with: $ncmInterface")
            Log.d(TAG, "Replaced OVPN_CONFIG_PATH with: $finalOvpnPath")
            
            // Create temporary file in app's cache directory
            val tempFile = File(context.cacheDir, SCRIPT_ASSET)
            FileOutputStream(tempFile).use { out ->
                out.write(scriptContent.toByteArray())
            }
            
            Log.d(TAG, "Wrote script to temp file: ${tempFile.absolutePath}")
            
            // Ensure root shell is initialized
            FileReaders.initRootShell()
            
            // Create target directory if needed
            val targetDir = File(targetPath).parent
            if (targetDir != null) {
                val mkdirCmd = "mkdir -p '$targetDir'"
                FileReaders.executeInRootShell(mkdirCmd)
                Log.d(TAG, "Created directory: $targetDir")
            }
            
            // Copy to target location using root
            val copyCmd = "cat '${tempFile.absolutePath}' > '$targetPath'"
            val copyResult = FileReaders.executeInRootShell(copyCmd)
            
            if (copyResult == null) {
                return InstallResult(
                    success = false,
                    message = "Failed to copy script (no root access?)"
                )
            }
            
            // Set executable permission
            val chmodCmd = "chmod 755 '$targetPath'"
            val chmodResult = FileReaders.executeInRootShell(chmodCmd)
            
            if (chmodResult == null) {
                return InstallResult(
                    success = false,
                    message = "Failed to set executable permission"
                )
            }
            
            // Verify installation
            if (FileReaders.exists(targetPath)) {
                Log.d(TAG, "Script successfully installed to $targetPath")
                
                // Clean up temp file
                tempFile.delete()
                
                return InstallResult(
                    success = true,
                    message = "Script installed successfully",
                    installedPath = targetPath
                )
            } else {
                return InstallResult(
                    success = false,
                    message = "Installation verification failed"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install script", e)
            return InstallResult(
                success = false,
                message = "Installation error: ${e.message}"
            )
        }
    }
    
    /**
     * Uninstall script
     * @param scriptPath Path to script (if null, will search common locations)
     * @return Uninstallation result
     */
    fun uninstall(scriptPath: String? = null): InstallResult {
        try {
            val pathsToRemove = if (scriptPath != null) {
                listOf(scriptPath)
            } else {
                listOf(INSTALL_PATH_DATA, INSTALL_PATH_SERVICE)
            }
            
            var removed = false
            val removedPaths = mutableListOf<String>()
            
            for (path in pathsToRemove) {
                if (FileReaders.exists(path)) {
                    val rmCmd = "rm -f '$path'"
                    val result = FileReaders.executeInRootShell(rmCmd)
                    
                    if (result != null && !FileReaders.exists(path)) {
                        Log.d(TAG, "Removed script from $path")
                        removed = true
                        removedPaths.add(path)
                    } else {
                        Log.w(TAG, "Failed to remove script from $path")
                    }
                }
            }
            
            return if (removed) {
                InstallResult(
                    success = true,
                    message = "Script uninstalled from: ${removedPaths.joinToString()}"
                )
            } else {
                InstallResult(
                    success = false,
                    message = "Script not found or already removed"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall script", e)
            return InstallResult(
                success = false,
                message = "Uninstallation error: ${e.message}"
            )
        }
    }
    
    /**
     * Execute the script
     * @param scriptPath Path to script (if null, will search common locations)
     * @param background Run in background (default: true)
     * @return Execution result
     */
    fun execute(scriptPath: String? = null, background: Boolean = true): InstallResult {
        try {
            val status = checkStatus()
            
            val execPath = scriptPath ?: status.installedPath ?: INSTALL_PATH_DATA
            
            if (!FileReaders.exists(execPath)) {
                return InstallResult(
                    success = false,
                    message = "Script not found at $execPath"
                )
            }
            
            if (!checkExecutable(execPath)) {
                // Try to fix permission
                FileReaders.executeInRootShell("chmod 755 '$execPath'")
            }
            
            // Check if already running
            if (checkRunning()) {
                return InstallResult(
                    success = false,
                    message = "Script is already running"
                )
            }
            
            val execCmd = if (background) {
                "sh '$execPath' &"
            } else {
                "sh '$execPath'"
            }
            
            val result = FileReaders.executeInRootShell(execCmd)
            
            if (result != null) {
                Log.d(TAG, "Script execution initiated")
                
                // Give it a moment to start
                Thread.sleep(500)
                
                val nowRunning = checkRunning()
                
                return InstallResult(
                    success = true,
                    message = if (nowRunning) {
                        "Script started successfully"
                    } else {
                        "Script executed (check logs for status)"
                    }
                )
            } else {
                return InstallResult(
                    success = false,
                    message = "Failed to execute script (no root access?)"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute script", e)
            return InstallResult(
                success = false,
                message = "Execution error: ${e.message}"
            )
        }
    }
    
    /**
     * Stop running script and OpenVPN
     * @return Stop result
     */
    fun stop(): InstallResult {
        try {
            val messages = mutableListOf<String>()
            
            // First, try to stop OpenVPN using PID file
            val openvpnPidPath = "/data/local/tmp/openvpn.pid"
            val pidResult = FileReaders.executeInRootShell("cat '$openvpnPidPath' 2>/dev/null")
            if (pidResult != null && pidResult.isNotBlank()) {
                val pid = pidResult.trim()
                Log.d(TAG, "Found OpenVPN PID: $pid")
                
                // Kill OpenVPN process
                val killOpenvpnCmd = "kill '$pid' 2>/dev/null || true"
                FileReaders.executeInRootShell(killOpenvpnCmd)
                messages.add("Stopped OpenVPN (PID: $pid)")
                
                // Remove PID file
                FileReaders.executeInRootShell("rm -f '$openvpnPidPath'")
            } else {
                // Fallback: try to kill by process name
                Log.d(TAG, "No PID file found, trying to kill OpenVPN by name")
                FileReaders.executeInRootShell("pkill -f 'openvpn.*--config' || true")
                messages.add("Stopped OpenVPN (by name)")
            }
            
            // Then kill the script process
            val killScriptCmd = "pkill -f 'vpn-hotspot-bridge.sh' || true"
            FileReaders.executeInRootShell(killScriptCmd)
            messages.add("Stopped script")
            
            // Give processes time to terminate
            Thread.sleep(500)
            
            val stillRunning = checkRunning()
            
            return InstallResult(
                success = !stillRunning,
                message = if (stillRunning) {
                    "Warning: Script may still be running. ${messages.joinToString(", ")}"
                } else {
                    messages.joinToString(", ")
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop script", e)
            return InstallResult(
                success = false,
                message = "Stop error: ${e.message}"
            )
        }
    }
    
    /**
     * Restart the script (useful after configuration changes)
     * @param scriptPath Path to script (if null, will search common locations)
     * @return Restart result
     */
    fun restart(scriptPath: String? = null): InstallResult {
        try {
            Log.d(TAG, "Restarting script...")
            
            // Stop current instance
            val stopResult = stop()
            if (!stopResult.success) {
                Log.w(TAG, "Stop during restart had issues: ${stopResult.message}")
            }
            
            // Wait a bit for cleanup
            Thread.sleep(1000)
            
            // Start again
            val startResult = execute(scriptPath, background = true)
            
            return InstallResult(
                success = startResult.success,
                message = "Restarted: ${startResult.message}"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart script", e)
            return InstallResult(
                success = false,
                message = "Restart error: ${e.message}"
            )
        }
    }
    
    /**
     * Read script log file
     * @param lines Number of lines to read (default: 50)
     * @return Log content
     */
    fun readLog(lines: Int = 50): String {
        return try {
            val cmd = "tail -n $lines '$LOG_PATH' 2>/dev/null || echo 'Log file not found'"
            FileReaders.executeInRootShell(cmd) ?: "Failed to read log"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }
    
    /**
     * Clear script log file
     * @return Clear result
     */
    fun clearLog(): InstallResult {
        return try {
            val cmd = "echo '' > '$LOG_PATH'"
            val result = FileReaders.executeInRootShell(cmd)
            
            InstallResult(
                success = result != null,
                message = if (result != null) "Log cleared" else "Failed to clear log"
            )
        } catch (e: Exception) {
            InstallResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Check if script file is executable
     */
    private fun checkExecutable(path: String): Boolean {
        val cmd = "test -x '$path' && echo '1' || echo '0'"
        val result = FileReaders.executeInRootShell(cmd)
        return result?.trim() == "1"
    }
    
    /**
     * Check if script process is running
     */
    private fun checkRunning(): Boolean {
        val cmd = "pgrep -f 'vpn-hotspot-bridge.sh' >/dev/null && echo '1' || echo '0'"
        val result = FileReaders.executeInRootShell(cmd)
        return result?.trim() == "1"
    }
}
