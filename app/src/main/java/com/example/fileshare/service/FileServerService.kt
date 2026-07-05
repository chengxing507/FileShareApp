package com.example.fileshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.example.fileshare.MainActivity
import com.example.fileshare.util.BinaryManager
import com.example.fileshare.util.NetworkUtil
import kotlinx.coroutines.*
import java.io.File

/**
 * 前台服务 — 管理 FileBrowser 进程的生命周期
 *
 * 通过 Intent Action 控制启停:
 *   ACTION_START  — 启动服务器
 *   ACTION_STOP   — 停止服务器
 *   ACTION_RESTART — 重启服务器（配置变更后）
 */
class FileServerService : LifecycleService() {

    companion object {
        const val TAG = "FileServerService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "file_server_channel"

        // Intent actions
        const val ACTION_START = "com.example.fileshare.action.START"
        const val ACTION_STOP = "com.example.fileshare.action.STOP"
        const val ACTION_RESTART = "com.example.fileshare.action.RESTART"

        // Intent extras
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_ROOT_DIR = "extra_root_dir"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"

        // Broadcast actions for UI communication
        const val BROADCAST_LOG = "com.example.fileshare.broadcast.LOG"
        const val BROADCAST_STATUS = "com.example.fileshare.broadcast.STATUS"

        // Broadcast extras
        const val EXTRA_LOG_LINE = "extra_log_line"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val EXTRA_IP = "extra_ip"
        const val EXTRA_PORT_BROADCAST = "extra_port"

        /**
         * 创建启动服务器的 Intent
         */
        fun createStartIntent(
            context: Context,
            port: Int = 8080,
            rootDir: String = "/sdcard/FileShare",
            username: String = "admin",
            password: String = "admin"
        ): Intent {
            return Intent(context, FileServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_ROOT_DIR, rootDir)
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_PASSWORD, password)
            }
        }

        /**
         * 创建停止服务器的 Intent
         */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, FileServerService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    // 配置参数
    private var configPort: Int = 8080
    private var configRootDir: String = "/sdcard/FileShare"
    private var configUsername: String = "admin"
    private var configPassword: String = "admin"

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null

    // ======================== 生命周期 ========================

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                // 读取配置
                configPort = intent.getIntExtra(EXTRA_PORT, 8080)
                configRootDir = intent.getStringExtra(EXTRA_ROOT_DIR) ?: "/sdcard/FileShare"
                configUsername = intent.getStringExtra(EXTRA_USERNAME) ?: "admin"
                configPassword = intent.getStringExtra(EXTRA_PASSWORD) ?: "admin"
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
            ACTION_RESTART -> {
                configPort = intent.getIntExtra(EXTRA_PORT, configPort)
                configRootDir = intent.getStringExtra(EXTRA_ROOT_DIR) ?: configRootDir
                configUsername = intent.getStringExtra(EXTRA_USERNAME) ?: configUsername
                configPassword = intent.getStringExtra(EXTRA_PASSWORD) ?: configPassword
                restartServer()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopServer()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    // ======================== 核心逻辑 ========================

    private fun startServer() {
        // 显示前台通知（正在启动）
        val notification = buildNotification("正在启动文件共享服务器...")
        startForeground(NOTIFICATION_ID, notification)

        serverJob = serviceScope.launch {
            try {
                // 1. 查找或解压二进制文件（优先使用 native lib 目录）
                val binaryFile = BinaryManager.findOrExtractBinary(this@FileServerService)
                val dbPath = File(filesDir, "filebrowser.db").absolutePath

                // 2. 通知 UI 正在启动
                sendStatusBroadcast(false)
                sendLogBroadcast("正在解压二进制文件...")
                sendLogBroadcast("数据库路径: $dbPath")
                sendLogBroadcast("共享目录: $configRootDir")

                // 3. 启动进程
                BinaryManager.startServer(
                    binaryPath = binaryFile.absolutePath,
                    port = configPort,
                    rootDir = configRootDir,
                    dbPath = dbPath,
                    username = configUsername,
                    password = configPassword
                ) { line ->
                    sendLogBroadcast(line)
                }

                // 短暂等待，确认进程正常启动
                delay(500)

                if (BinaryManager.isRunning()) {
                    // 4. 获取 IP 并更新通知
                    val ip = NetworkUtil.getLocalIpAddress(this@FileServerService)
                    val url = "http://$ip:${configPort}"
                    val notification = buildNotification("运行中: $url")
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)

                    sendStatusBroadcast(true, ip)
                    sendLogBroadcast("✓ 服务器启动成功！")
                    sendLogBroadcast("  地址: $url")
                    sendLogBroadcast("  用户名: $configUsername")
                    sendLogBroadcast("  密码: $configPassword")
                } else {
                    sendLogBroadcast("✗ 服务器进程未能启动")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                sendLogBroadcast("✗ 启动失败: ${e.message}")

                // 更新通知显示错误
                val notification = buildNotification("启动失败: ${e.message}")
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        BinaryManager.stopServer()
        sendStatusBroadcast(false)
        sendLogBroadcast("服务器已停止")
        Log.d(TAG, "Server stopped")
    }

    private fun restartServer() {
        sendLogBroadcast("正在重启服务器...")
        stopServer()
        startServer()
    }

    // ======================== 通知 ========================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "文件共享服务器",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "文件共享服务器运行状态通知"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("文件共享服务器")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ======================== 广播通信 ========================

    private fun sendLogBroadcast(line: String) {
        val intent = Intent(BROADCAST_LOG).apply {
            putExtra(EXTRA_LOG_LINE, line)
        }
        sendBroadcast(intent)
    }

    private fun sendStatusBroadcast(isRunning: Boolean, ip: String = "") {
        val intent = Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_IP, ip)
            putExtra(EXTRA_PORT_BROADCAST, configPort)
        }
        sendBroadcast(intent)
    }
}