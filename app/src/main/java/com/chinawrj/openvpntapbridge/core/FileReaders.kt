package com.chinawrj.openvpntapbridge.core

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 文件读取工具
 * 提供安全的文件读取功能，支持root权限读取
 */
object FileReaders {
    private const val TAG = "FileReaders"

    /**
     * 使用root权限执行命令
     */
    private fun executeAsRoot(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute as root: $command", e)
            null
        }
    }

    /**
     * 安全读取文本文件
     * 优先尝试直接读取，失败则尝试使用root权限
     */
    fun readTextSafe(path: String): String {
        // 先尝试直接读取
        try {
            val text = File(path).readText().trim()
            Log.d(TAG, "Read directly: $path -> $text")
            return text
        } catch (e: Exception) {
            Log.d(TAG, "Direct read failed for $path, trying root", e)
        }

        // 尝试使用root权限
        val text = executeAsRoot("cat '$path'")?.trim()
        if (text != null) {
            Log.d(TAG, "Read with root: $path -> $text")
            return text
        }

        Log.w(TAG, "Failed to read $path even with root")
        return ""
    }

    /**
     * 安全读取Long值
     */
    fun readLongSafe(path: String): Long {
        val text = readTextSafe(path)
        return text.toLongOrNull() ?: 0L
    }

    /**
     * 检查文件/目录是否存在
     */
    fun exists(path: String): Boolean {
        // 先尝试直接检查
        if (File(path).exists()) {
            return true
        }

        // 尝试使用root权限检查
        val result = executeAsRoot("test -e '$path' && echo 'exists' || echo 'notfound'")
        return result?.trim() == "exists"
    }

    /**
     * 读取符号链接的目标
     */
    fun readSymbolicLink(path: String): String {
        // 使用root权限读取（不使用 -f 参数以保留相对路径）
        val target = executeAsRoot("readlink '$path'")?.trim()
        if (target != null) {
            Log.d(TAG, "Read symlink with root: $path -> $target")
            return target
        }

        Log.w(TAG, "Failed to read symlink $path")
        return ""
    }

    /**
     * 列出目录中的所有文件/子目录
     */
    fun listDirectory(path: String): List<String> {
        // 先尝试直接读取
        try {
            val files = File(path).listFiles()
            if (files != null) {
                val names = files.map { it.name }
                Log.d(TAG, "List directory directly: $path -> $names")
                return names
            }
        } catch (e: Exception) {
            Log.d(TAG, "Direct directory listing failed for $path, trying root", e)
        }

        // 使用root权限读取
        val output = executeAsRoot("ls '$path'")
        if (output != null) {
            val names = output.lines().filter { it.isNotBlank() }
            Log.d(TAG, "List directory with root: $path -> $names")
            return names
        }

        Log.w(TAG, "Failed to list directory $path even with root")
        return emptyList()
    }
}
