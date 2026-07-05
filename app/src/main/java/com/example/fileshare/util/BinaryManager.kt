package com.example.fileshare.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 管理 FileBrowser 二进制文件的定位、权限设置和进程生命周期
 *
 * ⚠️ Android 8.0+ 对 /data/data/<pkg>/files 使用 noexec 挂载，
 * 普通文件无法获得执行权限。解决方案（优先级从高到低）：
 *
 * 1. nativeLibDir — APK 安装时系统自动提取，目录可执行（最可靠）
 * 2. /data/local/tmp/ — 部分 Android 版本可写可执行
 * 3. filesDir — 仅 Android 7.x 及以下可用
 */
object BinaryManager {

    private const val TAG = "BinaryManager"
    private const val BINARY_NAME = "filebrowser-arm64"
    private const val BINARY_NAME_SO = "libfilebrowser.so"
    private const val EXEC_DIR = "/data/local/tmp"

    private var process: Process? = null
    private var outputReaderThread: Thread? = null

    /**
     * 查找或解压二进制文件，返回可执行的 File 对象
     *
     * 查找优先级：
     *   1. nativeLibDir/libfilebrowser.so（APK 安装时系统提取，可执行）
     *   2. /data/local/tmp/filebrowser-arm64（可执行目录）
     *   3. filesDir/filebrowser-arm64（从 assets 解压，仅旧设备可用）
     */
    fun findOrExtractBinary(context: Context): File {
        // === 优先级 1: native lib 目录（系统提取，保证可执行）===
        val nativeLibDir = try {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .nativeLibDir
        } catch (e: Exception) {
            context.applicationInfo.nativeLibDir
        }
        val nativeLibFile = File(nativeLibDir, BINARY_NAME_SO)
        if (nativeLibFile.exists()) {
            Log.d(TAG, "✅ 使用 native lib: ${nativeLibFile.absolutePath}")
            return nativeLibFile
        }

        // === 优先级 2: 解压到 /data/local/tmp/ ===
        val tmpDir = File(EXEC_DIR)
        if (tmpDir.exists() && tmpDir.canWrite()) {
            val tmpFile = File(tmpDir, BINARY_NAME)
            return extractBinary(context, tmpFile)
        }

        // === 优先级 3: 兜底解压到 filesDir ===
        Log.w(TAG, "native lib 和 $EXEC_DIR 都不可用，回退到 filesDir")
        val fallbackFile = File(context.filesDir, BINARY_NAME)
        return extractBinary(context, fallbackFile)
    }

    /**
     * 从 assets 解压二进制文件到目标路径并设置可执行权限
     */
    private fun extractBinary(context: Context, targetFile: File): File {
        // 覆盖旧文件
        if (targetFile.exists()) {
            targetFile.delete()
        }

        // 确保父目录存在
        targetFile.parentFile?.mkdirs()

        // 从 assets 复制二进制
        context.assets.open(BINARY_NAME).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        // 设置可执行权限
        targetFile.setExecutable(true, false)
        targetFile.setReadable(true, false)
        targetFile.setWritable(true, false)

        // API 26+ 使用 Os.chmod 确保权限生效
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Os.chmod(targetFile.absolutePath, 493) // 0755
            } catch (e: Exception) {
                Log.w(TAG, "Os.chmod failed (may still work): ${e.message}")
            }
        }

        Log.d(TAG, "Binary ready at ${targetFile.absolutePath}")
        return targetFile
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