package com.chinawrj.openvpntapbridge.core

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * File reading utility
 * Provides safe file reading functionality with root permission support
 * Uses persistent root shell session to avoid frequent root permission requests
 */
object FileReaders {
    private const val TAG = "FileReaders"
    
    // Persistent root shell process
    private var rootProcess: Process? = null
    private var rootWriter: BufferedWriter? = null
    private var rootReader: BufferedReader? = null
    private var useRoot = false

    /**
     * Initialize root shell session (call su only once)
     */
    private fun initRootShell(): Boolean {
        if (rootProcess != null && rootProcess?.isAlive == true) {
            return true
        }

        return try {
            Log.d(TAG, "Initializing persistent root shell...")
            val process = Runtime.getRuntime().exec("su")
            rootProcess = process
            rootWriter = BufferedWriter(OutputStreamWriter(process.outputStream))
            rootReader = BufferedReader(InputStreamReader(process.inputStream))
            useRoot = true
            Log.d(TAG, "Root shell initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize root shell", e)
            useRoot = false
            false
        }
    }

    /**
     * Execute command using persistent root shell
     */
    private fun executeInRootShell(command: String): String? {
        if (!useRoot && !initRootShell()) {
            return null
        }

        return try {
            synchronized(this) {
                val writer = rootWriter ?: return null
                val reader = rootReader ?: return null

                // Send command
                writer.write(command)
                writer.newLine()
                writer.write("echo '<<<END_OF_COMMAND>>>'")
                writer.newLine()
                writer.flush()

                // Read output until end marker
                val output = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == "<<<END_OF_COMMAND>>>") {
                        break
                    }
                    if (output.isNotEmpty()) {
                        output.append('\n')
                    }
                    output.append(line)
                }

                output.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute in root shell: $command", e)
            // If error occurs, reset root shell
            closeRootShell()
            null
        }
    }

    /**
     * Close root shell session
     */
    fun closeRootShell() {
        try {
            rootWriter?.write("exit\n")
            rootWriter?.flush()
            rootWriter?.close()
            rootReader?.close()
            rootProcess?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing root shell", e)
        } finally {
            rootProcess = null
            rootWriter = null
            rootReader = null
        }
    }

    /**
     * Safely read text file
     * Try direct read first, fall back to root permission on failure
     */
    fun readTextSafe(path: String): String {
        // Try direct read first
        try {
            val text = File(path).readText().trim()
            return text
        } catch (e: Exception) {
            // Direct read failed, use root
        }

        // Try using root permission
        val text = executeInRootShell("cat '$path'")?.trim()
        if (text != null) {
            return text
        }

        Log.w(TAG, "Failed to read $path even with root")
        return ""
    }

    /**
     * Safely read Long value
     */
    fun readLongSafe(path: String): Long {
        val text = readTextSafe(path)
        return text.toLongOrNull() ?: 0L
    }

    /**
     * Check if file/directory exists
     */
    fun exists(path: String): Boolean {
        // Try direct check first
        if (File(path).exists()) {
            return true
        }

        // Try using root permission to check
        val result = executeInRootShell("[ -e '$path' ] && echo '1' || echo '0'")
        return result?.trim() == "1"
    }

    /**
     * Read symbolic link target
     */
    fun readlink(path: String): String? {
        // Use root permission to read (don't use -f parameter to preserve relative path)
        return executeInRootShell("readlink '$path'")
    }

    /**
     * List all files/subdirectories in directory
     */
    fun listDir(path: String): List<String> {
        // Try direct read first
        try {
            val files = File(path).listFiles()
            if (files != null) {
                val names = files.map { it.name }
                return names.toList()
            }
        } catch (e: Exception) {
            // Direct read failed, use root
        }

        // Use root permission to read
        val output = executeInRootShell("ls '$path'")
        if (output != null) {
            val names = output.lines().filter { it.isNotBlank() }
            return names
        }

        Log.w(TAG, "Failed to list directory $path even with root")
        return emptyList()
    }
}
