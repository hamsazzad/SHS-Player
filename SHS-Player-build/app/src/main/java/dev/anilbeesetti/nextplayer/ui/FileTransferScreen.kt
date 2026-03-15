package dev.anilbeesetti.nextplayer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID

fun generateQrCodeBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 2)
        }
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: WriterException) { null }
}

fun getWifiIpAddress(context: Context): String? {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ip = wm.connectionInfo.ipAddress
    if (ip == 0) return null
    return String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
}

data class SelectedMediaItem(val uri: Uri, val name: String, val size: Long, val type: String)

fun queryMediaItems(context: Context, uri: Uri, mediaType: String): SelectedMediaItem? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "file" else "file"
            val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
            SelectedMediaItem(uri, name, size, mediaType)
        }
    }.getOrNull()
}

class SimpleFileServer(
    private val context: Context,
    private val port: Int,
    val authToken: String = UUID.randomUUID().toString(),
) {
    private var serverSocket: ServerSocket? = null
    @Volatile var isRunning = false
    @Volatile private var _receivedFiles = listOf<File>()
    val receivedFiles: List<File> get() = _receivedFiles
    var onFileReceived: ((File) -> Unit)? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val client = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (!isRunning) break else continue
                    }
                    Thread { handleClient(client) }.start()
                }
            } catch (e: Exception) {
                android.util.Log.e("SimpleFileServer", "Server failed to start", e)
                isRunning = false
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun sanitizeFileName(raw: String): String {
        val stripped = raw.replace('/', '_').replace('\\', '_').replace('\u0000', '_')
        val name = File(stripped).name
        if (name.isBlank() || name == "." || name == "..") {
            return "received_${System.currentTimeMillis()}"
        }
        return name
    }

    private fun handleClient(socket: Socket) {
        try {
            val inputStream = socket.getInputStream()
            val headerBuf = StringBuilder()
            var prev3 = 0; var prev2 = 0; var prev1 = 0
            while (true) {
                val b = inputStream.read()
                if (b == -1) break
                headerBuf.append(b.toChar())
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break
                prev3 = prev2; prev2 = prev1; prev1 = b
            }
            val lines = headerBuf.toString().trimEnd().split("\r\n")
            val requestLine = lines.firstOrNull() ?: return
            val headers = mutableMapOf<String, String>()
            lines.drop(1).forEach { line ->
                val idx = line.indexOf(":")
                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }

            val clientToken = headers["x-auth-token"] ?: ""
            if (clientToken != authToken) {
                socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 12\r\n\r\nUnauthorized".toByteArray())
                return
            }

            if (requestLine.startsWith("POST")) {
                val rawName = headers["x-filename"] ?: "received_${System.currentTimeMillis()}"
                val fileName = sanitizeFileName(rawName)
                val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
                val recvDir = File(context.filesDir, "transfers/received")
                recvDir.mkdirs()
                val destFile = File(recvDir, fileName)
                val canonical = destFile.canonicalPath
                if (!canonical.startsWith(recvDir.canonicalPath + File.separator)) {
                    socket.getOutputStream().write("HTTP/1.1 400 Bad Request\r\nContent-Length: 16\r\n\r\nInvalid filename".toByteArray())
                    return
                }
                destFile.outputStream().use { out ->
                    val buf = ByteArray(8192)
                    var remaining = contentLength
                    while (remaining != 0L) {
                        val toRead = if (remaining > 0) minOf(buf.size.toLong(), remaining).toInt() else buf.size
                        val read = inputStream.read(buf, 0, toRead)
                        if (read == -1) break
                        out.write(buf, 0, read)
                        if (remaining > 0) remaining -= read
                    }
                }
                synchronized(this) { _receivedFiles = _receivedFiles + destFile }
                onFileReceived?.invoke(destFile)
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK".toByteArray())
            } else {
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 15\r\n\r\nSHS File Server".toByteArray())
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleFileServer", "Error handling client", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileTransferScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    var currentView by remember { mutableStateOf<TransferView>(TransferView.Main) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("File Transfer") },
            navigationIcon = {
                IconButton(onClick = {
                    if (currentView == TransferView.Main) onNavigateUp()
                    else currentView = TransferView.Main
                }) {
                    Icon(NextIcons.ArrowBack, contentDescription = "Back")
                }
            },
        )
        when (currentView) {
            TransferView.Main -> MainTransferView(
                onSend = { currentView = TransferView.Send },
                onReceive = { currentView = TransferView.Receive },
            )
            TransferView.Send -> SendView(context = context)
            TransferView.Receive -> ReceiveView(context = context)
        }
    }
}

sealed class TransferView {
    object Main : TransferView()
    object Send : TransferView()
    object Receive : TransferView()
}

@Composable
fun MainTransferView(onSend: () -> Unit, onReceive: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(NextIcons.Wifi, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Local File Transfer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Transfer files between devices on the same Wi-Fi network. No internet required.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(40.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TransferOptionCard(
                modifier = Modifier.weight(1f),
                icon = NextIcons.Upload,
                title = "Send",
                subtitle = "Share files from this device",
                onClick = onSend,
            )
            TransferOptionCard(
                modifier = Modifier.weight(1f),
                icon = NextIcons.ReceiveFile,
                title = "Receive",
                subtitle = "Get files from another device",
                onClick = onReceive,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text("1. Receiver taps 'Receive' — a QR code appears.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("2. Sender taps 'Send', selects files, then scans the QR code.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("3. Files transfer instantly over Wi-Fi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
fun TransferOptionCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick).height(140.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SendView(context: Context) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFiles by remember { mutableStateOf<List<SelectedMediaItem>>(emptyList()) }
    var targetUrl by remember { mutableStateOf("") }
    var transferStatus by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }

    val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val newItems = uris.mapNotNull { uri -> queryMediaItems(context, uri, "video") }
        selectedFiles = (selectedFiles + newItems).distinctBy { it.uri }
    }
    val musicPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val newItems = uris.mapNotNull { uri -> queryMediaItems(context, uri, "audio") }
        selectedFiles = (selectedFiles + newItems).distinctBy { it.uri }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onResult = { scannedUrl ->
                targetUrl = scannedUrl
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("Permissions Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("To send files, allow the following:")
                    Text("• Camera (scan QR code)", style = MaterialTheme.typography.bodySmall)
                    Text("• Location (for local network detection)", style = MaterialTheme.typography.bodySmall)
                    Text("• Bluetooth & Nearby Wi-Fi (device discovery)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermDialog = false; permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = { TextButton(onClick = { showPermDialog = false }) { Text("Cancel") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Videos") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Music") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (selectedTab == 0) videoPickerLauncher.launch("video/*")
                    else musicPickerLauncher.launch("audio/*")
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(painter = androidx.compose.ui.res.painterResource(dev.anilbeesetti.nextplayer.core.ui.R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Files")
            }
            if (selectedFiles.isNotEmpty()) {
                TextButton(onClick = { selectedFiles = emptyList() }) { Text("Clear All") }
            }
        }

        val displayFiles = if (selectedTab == 0) selectedFiles.filter { it.type == "video" } else selectedFiles.filter { it.type == "audio" }

        if (displayFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No files selected. Tap 'Add Files' to choose.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(displayFiles, key = { it.uri }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (item.type == "video") NextIcons.Video else NextIcons.Audio, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatFileSize(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectedFiles = selectedFiles.filter { it.uri != item.uri } }) {
                            Icon(NextIcons.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        if (transferStatus.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (transferStatus.startsWith("Error")) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(transferStatus, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = targetUrl,
            onValueChange = { targetUrl = it },
            label = { Text("Receiver URL (scan QR or enter manually)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    if (permissionsState.allPermissionsGranted) {
                        showQrScanner = true
                    } else {
                        showPermDialog = true
                    }
                }) {
                    Icon(NextIcons.QrCodeScanner, contentDescription = "Scan QR")
                }
            },
        )

        Button(
            onClick = {
                if (selectedFiles.isEmpty()) { transferStatus = "Please select files first."; return@Button }
                if (!permissionsState.allPermissionsGranted) { showPermDialog = true; return@Button }
                if (targetUrl.isBlank()) { transferStatus = "Please enter or scan the receiver's URL."; return@Button }
                isSending = true
                transferStatus = "Sending ${selectedFiles.size} file(s)..."
                scope.launch(Dispatchers.IO) {
                    var sent = 0; var failed = 0
                    val parsedUrl = Uri.parse(targetUrl)
                    val token = parsedUrl.getQueryParameter("token") ?: ""
                    val baseUrl = targetUrl.substringBefore("?")
                    selectedFiles.forEach { item ->
                        runCatching {
                            val conn = URL("$baseUrl/upload").openConnection() as java.net.HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.doOutput = true
                            conn.setRequestProperty("X-Filename", item.name)
                            conn.setRequestProperty("X-Auth-Token", token)
                            conn.setRequestProperty("Content-Type", "application/octet-stream")
                            conn.connectTimeout = 10000; conn.readTimeout = 60000
                            context.contentResolver.openInputStream(item.uri)?.use { input ->
                                conn.outputStream.use { out -> input.copyTo(out) }
                            }
                            val code = conn.responseCode
                            conn.disconnect()
                            if (code == 200) sent++ else failed++
                        }.onFailure { failed++ }
                    }
                    withContext(Dispatchers.Main) {
                        isSending = false
                        transferStatus = if (failed == 0) "✓ Successfully sent $sent file(s)!"
                        else "Sent: $sent, Failed: $failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending && selectedFiles.isNotEmpty(),
        ) {
            if (isSending) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)) }
            Text(if (isSending) "Sending..." else "Send ${selectedFiles.size} File(s)")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ReceiveView(context: Context) {
    val scope = rememberCoroutineScope()
    var serverState by remember { mutableStateOf<ReceiveServerState>(ReceiveServerState.Idle) }
    var receivedFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var server by remember { mutableStateOf<SimpleFileServer?>(null) }
    var showPermDialog by remember { mutableStateOf(false) }

    val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionsState = rememberMultiplePermissionsState(permissions = requiredPermissions)

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("Permissions Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("To receive files, allow the following:")
                    Text("• Location (for local network detection)", style = MaterialTheme.typography.bodySmall)
                    Text("• Bluetooth & Nearby Wi-Fi (device discovery)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermDialog = false; permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = { TextButton(onClick = { showPermDialog = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val state = serverState) {
            is ReceiveServerState.Idle -> {
                Spacer(modifier = Modifier.height(40.dp))
                Icon(NextIcons.ReceiveFile, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Ready to Receive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Tap 'Start Receiving' to generate a QR code. The sender will scan it to connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (!permissionsState.allPermissionsGranted) { showPermDialog = true; return@Button }
                        scope.launch(Dispatchers.IO) {
                            try {
                                val port = (10000..65000).random()
                                val newServer = SimpleFileServer(context, port)
                                newServer.start()
                                val ip = getWifiIpAddress(context) ?: "0.0.0.0"
                                val url = "http://$ip:$port?token=${newServer.authToken}"
                                val qr = generateQrCodeBitmap(url)
                                withContext(Dispatchers.Main) {
                                    server = newServer
                                    serverState = ReceiveServerState.Running(url, qr, port)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    serverState = ReceiveServerState.Error("Failed to start: ${e.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start Receiving") }
            }
            is ReceiveServerState.Running -> {
                Text("Scan this QR code to send files here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                state.qrBitmap?.let { bm ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 4.dp,
                        modifier = Modifier.size(256.dp),
                    ) {
                        Image(bitmap = bm.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize().padding(8.dp))
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Server URL:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(state.serverUrl, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Port: ${state.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                var liveFileNames by remember { mutableStateOf<List<String>>(emptyList()) }
                val activeServer = server
                LaunchedEffect(activeServer) {
                    if (activeServer == null) return@LaunchedEffect
                    while (activeServer.isRunning) {
                        liveFileNames = activeServer.receivedFiles.map { it.name }
                        kotlinx.coroutines.delay(500)
                    }
                    liveFileNames = activeServer.receivedFiles.map { it.name }
                }
                if (liveFileNames.isNotEmpty()) {
                    Text("Received ${liveFileNames.size} file(s):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(liveFileNames) { name ->
                            Text("• $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        server?.stop()
                        server = null
                        serverState = ReceiveServerState.Idle
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Stop Server") }
            }
            is ReceiveServerState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = { serverState = ReceiveServerState.Idle }) { Text("Try Again") }
            }
        }
    }
}

sealed class ReceiveServerState {
    object Idle : ReceiveServerState()
    data class Running(val serverUrl: String, val qrBitmap: Bitmap?, val port: Int) : ReceiveServerState()
    data class Error(val message: String) : ReceiveServerState()
}
