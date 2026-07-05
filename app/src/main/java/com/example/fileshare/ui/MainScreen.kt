package com.example.fileshare.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.fileshare.service.FileServerService
import com.example.fileshare.util.NetworkUtil

/**
 * 主界面 — 文件共享服务器的控制面板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current

    // ========== 状态 ==========
    var isRunning by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var rootDir by remember { mutableStateOf("/sdcard/FileShare") }
    var logs by remember { mutableStateOf(listOf("就绪 ✓  点击下方按钮启动文件共享服务器")) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    // 登录凭据
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("admin") }

    // 日志列表状态
    val logListState = rememberLazyListState()

    // ========== BroadcastReceiver ==========
    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    FileServerService.BROADCAST_LOG -> {
                        val line = intent.getStringExtra(FileServerService.EXTRA_LOG_LINE) ?: return
                        logs = logs + line
                    }
                    FileServerService.BROADCAST_STATUS -> {
                        isRunning = intent.getBooleanExtra(FileServerService.EXTRA_IS_RUNNING, false)
                        val ip = intent.getStringExtra(FileServerService.EXTRA_IP) ?: ""
                        if (ip.isNotEmpty()) ipAddress = ip
                        val p = intent.getIntExtra(FileServerService.EXTRA_PORT_BROADCAST, 8080)
                        port = p.toString()
                    }
                }
            }
        }
    }

    // 注册/注销 BroadcastReceiver
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(FileServerService.BROADCAST_LOG)
            addAction(FileServerService.BROADCAST_STATUS)
        }
        // 使用 RECEIVER_EXPORTED 以便接收来自 Service 的广播
        context.registerReceiver(receiver, filter, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        })
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // 初始化时获取 IP
    LaunchedEffect(Unit) {
        ipAddress = NetworkUtil.getLocalIpAddress(context)
    }

    // 日志自动滚动
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    // ========== 权限请求 ==========
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // ========== SAF 目录选择 ==========
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val resolvedPath = resolveDirectoryPath(uri)
            if (resolvedPath != null) {
                rootDir = resolvedPath
                logs = logs + "已选择目录: $resolvedPath"
            } else {
                logs = logs + "⚠ 无法解析目录路径，请手动输入"
            }
        }
    }

    // ========== UI ==========
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("文件共享")
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusDot(isRunning = isRunning)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showPasswordDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "账号设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== 连接信息卡片 =====
            AnimatedVisibility(
                visible = isRunning && ipAddress.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // URL
                        val url = "http://$ipAddress:$port"
                        Text(
                            text = "📡 同一局域网内访问",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = url,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "账号: $username / 密码: $password",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // QR 码
                        QrCodeView(url = url, size = 200)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "扫码快速访问",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== 配置区域 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 共享目录
                    OutlinedTextField(
                        value = rootDir,
                        onValueChange = { rootDir = it },
                        label = { Text("共享目录路径") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "浏览")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRunning
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 端口
                    OutlinedTextField(
                        value = port,
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() }
                            if (filtered.length <= 5 && (filtered.isEmpty() || filtered.toInt() in 1..65535)) {
                                port = filtered
                            }
                        },
                        label = { Text("端口") },
                        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isRunning,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ===== 控制按钮 =====
            Button(
                onClick = {
                    if (isRunning) {
                        // 停止
                        context.startForegroundService(FileServerService.createStopIntent(context))
                    } else {
                        // 检查权限
                        checkAndRequestPermissions(
                            context = context,
                            onNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onManageStoragePermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    if (!android.os.Environment.isExternalStorageManager()) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        manageStorageLauncher.launch(intent)
                                    }
                                }
                            }
                        )

                        // 启动
                        val p = port.toIntOrNull() ?: 8080
                        context.startForegroundService(
                            FileServerService.createStartIntent(
                                context = context,
                                port = p,
                                rootDir = rootDir,
                                username = username,
                                password = password
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "停止服务器" else "启动服务器",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isRunning) "点击停止将终止文件共享服务"
                else "启动后同一局域网设备可访问共享文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 日志区域 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 运行日志",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                if (logs.isNotEmpty()) {
                    TextButton(onClick = {
                        logs = listOf("日志已清空")
                    }) {
                        Text("清空", fontSize = 12.sp)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 120.dp, max = 260.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    state = logListState
                ) {
                    items(logs.takeLast(200)) { line ->
                        Text(
                            text = line,
                            color = Color(0xFF00E676),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ========== 密码设置对话框 ==========
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("登录凭据设置") },
            text = {
                Column {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning
                    )
                    if (isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠ 请先停止服务器再修改凭据",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

/**
 * 状态指示圆点
 */
@Composable
fun StatusDot(isRunning: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (isRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
    )
}

/**
 * 检查并请求所需权限
 */
private fun checkAndRequestPermissions(
    context: Context,
    onNotificationPermission: () -> Unit,
    onManageStoragePermission: () -> Unit
) {
    // Android 13+ 通知权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onNotificationPermission()
        }
    }

    // Android 11+ 管理所有文件权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!android.os.Environment.isExternalStorageManager()) {
            onManageStoragePermission()
        }
    }
}

/**
 * 尝试将 SAF 返回的 content URI 解析为文件系统路径
 */
private fun resolveDirectoryPath(uri: android.net.Uri): String? {
    val path = uri.path ?: return null

    return when {
        // 格式: /tree/primary:Documents
        path.startsWith("/tree/primary:") -> {
            val subPath = path.removePrefix("/tree/primary:")
            if (subPath.isEmpty()) "/sdcard" else "/sdcard/$subPath"
        }
        // 格式: /tree/XXXX-XXXX:path
        path.contains(":") -> {
            val parts = path.split(":")
            if (parts.size >= 2) {
                val storageId = parts[0].removePrefix("/tree/")
                "/storage/$storageId/${parts[1]}"
            } else null
        }
        else -> null
    }
}