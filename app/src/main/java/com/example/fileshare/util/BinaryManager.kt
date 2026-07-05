package com.example.fileshare.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 管理 FileBrowser 二进制文件的解压、权限设置和进程生命周期
 */
object BinaryManager {

    private const val TAG = "BinaryManager"
    private const val BINARY_NAME = "filebrowser-arm64"

    private var process: Process? = null
    private var outputReaderThread: Thread? = null

    /**
     * 从 assets 解压二进制文件到 app 私有目录并赋予可执行权限
     */
    fun extractBinary(context: Context): File {
        val binaryFile = File(context.filesDir, BINARY_NAME)

        // 如果已存在则覆盖更新
        if (binaryFile.exists()) {
            binaryFile.delete()
        }

        context.assets.open(BINARY_NAME).use { input ->
            FileOutputStream(binaryFile).use { output ->
                input.copyTo(output)
            }
        }

        // 设置可执行权限
        binaryFile.setExecutable(true, false)
        Log.d(TAG, "Binary extracted to ${binaryFile.absolutePath}")
        return binaryFile
    }

    /**
     * 启动 FileBrowser 进程
     *
     * @param binaryPath 二进制文件路径
     * @param port 监听端口
     * @param rootDir 共享根目录
     * @param dbPath 数据库文件路径
     * @param username 管理员用户名
     * @param password 管理员密码
     * @param listener 日志回调
     * @return 启动的 Process 对象
     */
    fun startServer(
        binaryPath: String,
        port: Int,
        rootDir: String,
        dbPath: String,
        username: String = "admin",
        password: String = "admin",
        listener: (String) -> Unit = {}
    ): Process? {
        // 如果已有进程先停止
        if (process?.isAlive == true) {
            stopServer()
        }

        // 确保共享目录存在
        val rootFile = File(rootDir)
        if (!rootFile.exists()) {
            rootFile.mkdirs()
        }

        val command = listOf(
            binaryPath,
            "-a", "0.0.0.0",
            "-p", port.toString(),
            "-r", rootDir,
            "-d", dbPath,
            "--username", username,
            "--password", password
        )

        Log.d(TAG, "Starting server: ${command.joinToString(" ")}")

        val pb = ProcessBuilder(command)
        pb.directory(File(binaryPath).parentFile)
        pb.redirectErrorStream(true)

        process = pb.start()

        // 在后台线程读取进程输出
        outputReaderThread = Thread {
            try {
                process?.inputStream?.bufferedReader()?.use { reader ->
                    reader.lines().forEach { line ->
                        Log.d(TAG, "[FileBrowser] $line")
                        listener(line)
                    }
                }
            } catch (e: Exception) {
                if (e !is java.io.IOException || process?.isAlive == true) {
                    Log.e(TAG, "Error reading process output", e)
                    listener("错误: ${e.message}")
                }
            }
        }.apply {
            isDaemon = true
            name = "filebrowser-output-reader"
            start()
        }

        return process
    }

    /**
     * 停止服务器进程
     */
    fun stopServer() {
        outputReaderThread?.interrupt()
        process?.let {
            if (it.isAlive) {
                it.destroyForcibly()
                Log.d(TAG, "Server process destroyed")
            }
        }
        process = null
        outputReaderThread = null
    }

    /**
     * 检查服务器是否正在运行
     */
    fun isRunning(): Boolean = process?.isAlive == true

    /**
     * 获取进程退出码（如果已退出）
     */
    fun exitValue(): Int? {
        return try {
            process?.exitValue()
        } catch (e: IllegalThreadStateException) {
            null // 进程仍在运行
        }
    }
}