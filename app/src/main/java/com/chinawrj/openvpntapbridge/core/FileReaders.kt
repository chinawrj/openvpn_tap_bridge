package com.chinawrj.openvpntapbridge.core

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * 文件读取工具
 * 提供安全的文件读取功能，支持root权限读取
 * 使用持久的root shell会话，避免频繁请求root权限
 */
object FileReaders {
    private const val TAG = "FileReaders"
    
    // 持久的root shell进程
    private var rootProcess: Process? = null
    private var rootWriter: BufferedWriter? = null
    private var rootReader: BufferedReader? = null
    private var useRoot = false

    /**
     * 初始化root shell会话（只调用一次su）
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
     * 使用持久的root shell执行命令
     */
    private fun executeInRootShell(command: String): String? {
        if (!useRoot && !initRootShell()) {
            return null
        }

        return try {
            synchronized(this) {
                val writer = rootWriter ?: return null
                val reader = rootReader ?: return null

                // 发送命令
                writer.write(command)
                writer.newLine()
                writer.write("echo '<<<END_OF_COMMAND>>>'")
                writer.newLine()
                writer.flush()

                // 读取输出直到结束标记
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
            // 如果出错，重置root shell
            closeRootShell()
            null
        }
    }

    /**
     * 关闭root shell会话
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
     * 安全读取文本文件
     * 优先尝试直接读取，失败则尝试使用root权限
     */
    fun readTextSafe(path: String): String {
        // 先尝试直接读取
        try {
            val text = File(path).readText().trim()
            return text
        } catch (e: Exception) {
            // 直接读取失败，使用root
        }

        // 尝试使用root权限
        val text = executeInRootShell("cat '$path'")?.trim()
        if (text != null) {
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
        val result = executeInRootShell("test -e '$path' && echo 'exists' || echo 'notfound'")
        return result?.trim() == "exists"
    }

    /**
     * 读取符号链接的目标
     */
    fun readSymbolicLink(path: String): String {
        // 使用root权限读取（不使用 -f 参数以保留相对路径）
        val target = executeInRootShell("readlink '$path'")?.trim()
        if (target != null) {
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
                return names
            }
        } catch (e: Exception) {
            // 直接读取失败，使用root
        }

        // 使用root权限读取
        val output = executeInRootShell("ls '$path'")
        if (output != null) {
            val names = output.lines().filter { it.isNotBlank() }
            return names
        }

        Log.w(TAG, "Failed to list directory $path even with root")
        return emptyList()
    }
}
