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
    
    // Installation locations
    const val INSTALL_PATH_DATA = "/data/local/tmp/vpn-hotspot-bridge.sh"
    const val INSTALL_PATH_SERVICE = "/data/adb/service.d/vpn-hotspot-bridge.sh"
    
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
     * @param preferredPath Preferred installation path (default: /data/local/tmp)
     * @return Script status
     */
    fun checkStatus(preferredPath: String = INSTALL_PATH_DATA): ScriptStatus {
        val paths = listOf(preferredPath, INSTALL_PATH_DATA, INSTALL_PATH_SERVICE)
        
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
     * @param targetPath Target installation path
     * @return Installation result
     */
    fun install(context: Context, targetPath: String = INSTALL_PATH_DATA): InstallResult {
        try {
            // Check if already installed
            if (FileReaders.exists(targetPath)) {
                Log.d(TAG, "Script already exists at $targetPath")
                return InstallResult(
                    success = false,
                    message = "Script already installed at $targetPath"
                )
            }
            
            // Read script from assets
            val scriptContent = context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
            
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
     * Stop running script
     * @return Stop result
     */
    fun stop(): InstallResult {
        try {
            // Kill processes matching the script name
            val killCmd = "pkill -f 'vpn-hotspot-bridge.sh'"
            val result = FileReaders.executeInRootShell(killCmd)
            
            if (result != null) {
                // Give it a moment to stop
                Thread.sleep(500)
                
                val stillRunning = checkRunning()
                
                return InstallResult(
                    success = !stillRunning,
                    message = if (stillRunning) {
                        "Failed to stop script (still running)"
                    } else {
                        "Script stopped successfully"
                    }
                )
            } else {
                return InstallResult(
                    success = false,
                    message = "Failed to stop script (no root access?)"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop script", e)
            return InstallResult(
                success = false,
                message = "Stop error: ${e.message}"
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
