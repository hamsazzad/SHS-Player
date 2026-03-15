package dev.anilbeesetti.nextplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentUris
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

// ─── Constants ─────────────────────────────────────────────────────────────────
private const val PREFS_VAULT = "privacy_vault_prefs"
private const val KEY_PASSWORD_HASH = "password_hash"
private const val KEY_SECURITY_Q = "security_question"
private const val KEY_SECURITY_A_HASH = "security_answer_hash"
private const val KEY_FILE_META = "vault_files"
private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

private val SECURITY_QUESTIONS = listOf(
    "What is your mother's maiden name?",
    "What was the name of your first pet?",
    "What city were you born in?",
    "What was the name of your first school?",
    "What is your oldest sibling's middle name?",
)

// ─── Data Classes ─────────────────────────────────────────────────────────────
data class VaultFile(
    val id: String,
    val name: String,
    val originalPath: String,
    val type: String, // "video" or "music"
    val size: Long,
    val vaultPath: String,
)

// ─── Helpers ─────────────────────────────────────────────────────────────────
fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

fun getVaultPrefs(context: Context) = context.getSharedPreferences(PREFS_VAULT, Context.MODE_PRIVATE)

fun isVaultSetup(context: Context): Boolean =
    getVaultPrefs(context).contains(KEY_PASSWORD_HASH)

fun verifyVaultPassword(context: Context, password: String): Boolean =
    getVaultPrefs(context).getString(KEY_PASSWORD_HASH, "") == sha256(password)

fun setupVault(context: Context, password: String, question: String, answer: String) {
    getVaultPrefs(context).edit()
        .putString(KEY_PASSWORD_HASH, sha256(password))
        .putString(KEY_SECURITY_Q, question)
        .putString(KEY_SECURITY_A_HASH, sha256(answer.lowercase().trim()))
        .apply()
    getVaultDir(context, "videos").mkdirs()
    getVaultDir(context, "music").mkdirs()
}

fun getVaultDir(context: Context, type: String): File =
    File(context.filesDir, "vault/$type")

fun getVaultFiles(context: Context, type: String): List<VaultFile> {
    val prefs = getVaultPrefs(context)
    val raw = prefs.getStringSet("${KEY_FILE_META}_$type", emptySet()) ?: return emptyList()
    return raw.mapNotNull { s ->
        runCatching {
            val parts = s.split("|")
            VaultFile(
                id = parts[0],
                name = parts[1],
                originalPath = parts[2],
                type = parts[3],
                size = parts[4].toLong(),
                vaultPath = parts[5],
            )
        }.getOrNull()
    }
}

fun saveVaultFileMeta(context: Context, type: String, files: List<VaultFile>) {
    val prefs = getVaultPrefs(context)
    val set = files.map { f ->
        "${f.id}|${f.name}|${f.originalPath}|${f.type}|${f.size}|${f.vaultPath}"
    }.toSet()
    prefs.edit().putStringSet("${KEY_FILE_META}_$type", set).apply()
}

fun moveToVault(context: Context, uri: Uri, type: String): VaultFile? {
    return runCatching {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use { c ->
            if (!c.moveToFirst()) return null
            val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pathIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            val name = c.getString(nameIdx) ?: "unknown"
            val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
            val originalPath = if (pathIdx >= 0) c.getString(pathIdx) ?: "" else ""
            val id = System.currentTimeMillis().toString()
            val vaultDir = getVaultDir(context, type)
            vaultDir.mkdirs()
            val destFile = File(vaultDir, "$id-$name")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            context.contentResolver.delete(uri, null, null)
            val vaultFile = VaultFile(id, name, originalPath, type, size, destFile.absolutePath)
            val existing = getVaultFiles(context, type).toMutableList()
            existing.add(vaultFile)
            saveVaultFileMeta(context, type, existing)
            vaultFile
        }
    }.getOrNull()
}

fun restoreFromVault(context: Context, vaultFile: VaultFile) {
    runCatching {
        val srcFile = File(vaultFile.vaultPath)
        if (!srcFile.exists()) return
        val type = if (vaultFile.type == "video") "video/mp4" else "audio/mpeg"
        val mimeBase = if (vaultFile.type == "video") MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, vaultFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, type)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(mimeBase, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { out ->
            srcFile.inputStream().use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        srcFile.delete()
        val remaining = getVaultFiles(context, vaultFile.type).filter { it.id != vaultFile.id }
        saveVaultFileMeta(context, vaultFile.type, remaining)
    }
}

fun deleteFromVault(context: Context, vaultFile: VaultFile) {
    runCatching {
        File(vaultFile.vaultPath).delete()
        val remaining = getVaultFiles(context, vaultFile.type).filter { it.id != vaultFile.id }
        saveVaultFileMeta(context, vaultFile.type, remaining)
    }
}

fun playVaultFile(context: Context, vaultFile: VaultFile, mimeType: String) {
    runCatching {
        val file = File(vaultFile.vaultPath)
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        android.widget.Toast.makeText(context, "Cannot play: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// ─── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyFolderScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    var screenState by remember { mutableStateOf<PrivacyFolderState>(PrivacyFolderState.Loading) }

    LaunchedEffect(Unit) {
        screenState = if (isVaultSetup(context)) {
            val biometricEnabled = getVaultPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
            PrivacyFolderState.Login(biometricEnabled)
        } else {
            PrivacyFolderState.Setup
        }
    }

    when (val state = screenState) {
        is PrivacyFolderState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is PrivacyFolderState.Setup -> {
            SetupScreen(
                modifier = modifier,
                onNavigateUp = onNavigateUp,
                onSetupComplete = { screenState = PrivacyFolderState.Unlocked },
            )
        }
        is PrivacyFolderState.Login -> {
            LoginScreen(
                modifier = modifier,
                onNavigateUp = onNavigateUp,
                biometricEnabled = state.biometricEnabled,
                onLoginSuccess = { screenState = PrivacyFolderState.Unlocked },
            )
        }
        is PrivacyFolderState.Unlocked -> {
            VaultContentScreen(
                modifier = modifier,
                onNavigateUp = onNavigateUp,
            )
        }
    }
}

sealed class PrivacyFolderState {
    object Loading : PrivacyFolderState()
    object Setup : PrivacyFolderState()
    data class Login(val biometricEnabled: Boolean) : PrivacyFolderState()
    object Unlocked : PrivacyFolderState()
}

// ─── Setup Screen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onSetupComplete: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var selectedQuestion by remember { mutableStateOf(SECURITY_QUESTIONS[0]) }
    var securityAnswer by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var showQuestionMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Setup Privacy Folder") },
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(NextIcons.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / 2f },
                modifier = Modifier.fillMaxWidth(),
            )
            when (step) {
                0 -> {
                    Text("Security Question", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("This will help you recover your password if forgotten.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { showQuestionMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(selectedQuestion, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(NextIcons.ChevronRight, contentDescription = null)
                        }
                        DropdownMenu(expanded = showQuestionMenu, onDismissRequest = { showQuestionMenu = false }) {
                            SECURITY_QUESTIONS.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q) },
                                    onClick = { selectedQuestion = q; showQuestionMenu = false },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = securityAnswer,
                        onValueChange = { securityAnswer = it; errorMsg = "" },
                        label = { Text("Your Answer") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (errorMsg.isNotEmpty()) Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            if (securityAnswer.isBlank()) { errorMsg = "Please provide an answer."; return@Button }
                            step = 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next") }
                }
                1 -> {
                    Text("Create Password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Set a password to protect your private files.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = "" },
                        label = { Text("Password (min 4 characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = "" },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (errorMsg.isNotEmpty()) Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            when {
                                password.length < 4 -> errorMsg = "Password must be at least 4 characters."
                                password != confirmPassword -> errorMsg = "Passwords do not match."
                                else -> {
                                    setupVault(context, password, selectedQuestion, securityAnswer)
                                    onSetupComplete()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Create Privacy Folder") }
                    OutlinedButton(
                        onClick = { step = 0 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back") }
                }
            }
        }
    }
}

// ─── Login Screen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    biometricEnabled: Boolean,
    onLoginSuccess: () -> Unit,
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotAnswer by remember { mutableStateOf("") }
    var forgotNewPassword by remember { mutableStateOf("") }
    var forgotError by remember { mutableStateOf("") }

    val activity = context as? FragmentActivity

    fun launchBiometric() {
        activity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onLoginSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    errorMsg = errString.toString()
                }
            }
            override fun onAuthenticationFailed() {
                errorMsg = "Authentication failed. Try again."
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Privacy Folder")
            .setSubtitle("Authenticate to unlock")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    LaunchedEffect(biometricEnabled) {
        if (biometricEnabled) launchBiometric()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Privacy Folder") },
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(NextIcons.ArrowBack, contentDescription = "Back")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(NextIcons.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Enter Password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = "" },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMsg.isNotEmpty(),
            )
            if (errorMsg.isNotEmpty()) {
                Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (verifyVaultPassword(context, password)) onLoginSuccess()
                    else errorMsg = "Incorrect password. Try again."
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Unlock") }
            Spacer(modifier = Modifier.height(8.dp))
            if (biometricEnabled) {
                OutlinedButton(
                    onClick = { launchBiometric() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(NextIcons.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Fingerprint / Device PIN")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            TextButton(onClick = { showForgotDialog = true }) {
                Text("Forgot Password?")
            }
        }
    }

    if (showForgotDialog) {
        val prefs = getVaultPrefs(context)
        val question = prefs.getString(KEY_SECURITY_Q, "") ?: ""
        AlertDialog(
            onDismissRequest = { showForgotDialog = false; forgotAnswer = ""; forgotNewPassword = ""; forgotError = "" },
            title = { Text("Reset Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = forgotAnswer,
                        onValueChange = { forgotAnswer = it; forgotError = "" },
                        label = { Text("Your Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = forgotNewPassword,
                        onValueChange = { forgotNewPassword = it; forgotError = "" },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (forgotError.isNotEmpty()) Text(forgotError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val storedAnswerHash = prefs.getString(KEY_SECURITY_A_HASH, "")
                    when {
                        sha256(forgotAnswer.lowercase().trim()) != storedAnswerHash -> forgotError = "Incorrect answer."
                        forgotNewPassword.length < 4 -> forgotError = "Password too short."
                        else -> {
                            prefs.edit().putString(KEY_PASSWORD_HASH, sha256(forgotNewPassword)).apply()
                            showForgotDialog = false
                        }
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") } },
        )
    }
}

// ─── Vault Content Screen ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultContentScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Music")
    var videoFiles by remember { mutableStateOf(getVaultFiles(context, "videos")) }
    var musicFiles by remember { mutableStateOf(getVaultFiles(context, "music")) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showVideoPicker by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showVideoPicker) {
        MediaStorePickerDialog(
            mediaType = "video",
            title = "Select Videos",
            onDismiss = { showVideoPicker = false },
            onConfirm = { uris ->
                showVideoPicker = false
                uris.forEach { uri ->
                    scope.launch(Dispatchers.IO) {
                        moveToVault(context, uri, "videos")
                        withContext(Dispatchers.Main) { videoFiles = getVaultFiles(context, "videos") }
                    }
                }
            },
        )
    }

    if (showMusicPicker) {
        MediaStorePickerDialog(
            mediaType = "audio",
            title = "Select Music",
            onDismiss = { showMusicPicker = false },
            onConfirm = { uris ->
                showMusicPicker = false
                uris.forEach { uri ->
                    scope.launch(Dispatchers.IO) {
                        moveToVault(context, uri, "music")
                        withContext(Dispatchers.Main) { musicFiles = getVaultFiles(context, "music") }
                    }
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Privacy Folder") },
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(NextIcons.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(painter = painterResource(coreUiR.drawable.ic_add), contentDescription = "Add files")
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add Videos") },
                            leadingIcon = { Icon(NextIcons.Video, contentDescription = null) },
                            onClick = { showAddMenu = false; showVideoPicker = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Add Music") },
                            leadingIcon = { Icon(NextIcons.Audio, contentDescription = null) },
                            onClick = { showAddMenu = false; showMusicPicker = true },
                        )
                    }
                }
            },
        )
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedTab) {
            0 -> VaultFileList(
                files = videoFiles,
                emptyText = "No private videos.\nTap + to add videos.",
                onRestore = { f ->
                    scope.launch(Dispatchers.IO) {
                        restoreFromVault(context, f)
                        withContext(Dispatchers.Main) { videoFiles = getVaultFiles(context, "videos") }
                    }
                },
                onDelete = { f ->
                    scope.launch(Dispatchers.IO) {
                        deleteFromVault(context, f)
                        withContext(Dispatchers.Main) { videoFiles = getVaultFiles(context, "videos") }
                    }
                },
                onPlay = { f -> playVaultFile(context, f, "video/*") },
            )
            1 -> VaultFileList(
                files = musicFiles,
                emptyText = "No private music.\nTap + to add music.",
                onRestore = { f ->
                    scope.launch(Dispatchers.IO) {
                        restoreFromVault(context, f)
                        withContext(Dispatchers.Main) { musicFiles = getVaultFiles(context, "music") }
                    }
                },
                onDelete = { f ->
                    scope.launch(Dispatchers.IO) {
                        deleteFromVault(context, f)
                        withContext(Dispatchers.Main) { musicFiles = getVaultFiles(context, "music") }
                    }
                },
                onPlay = { f -> playVaultFile(context, f, "audio/*") },
            )
        }
    }
}

@Composable
fun VaultFileList(
    files: List<VaultFile>,
    emptyText: String,
    onRestore: (VaultFile) -> Unit,
    onDelete: (VaultFile) -> Unit,
    onPlay: (VaultFile) -> Unit = {},
) {
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(files, key = { it.id }) { file ->
            VaultFileItem(file = file, onRestore = { onRestore(file) }, onDelete = { onDelete(file) }, onPlay = { onPlay(file) })
        }
    }
}

@Composable
fun VaultFileItem(
    file: VaultFile,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (file.type == "video") NextIcons.Video else NextIcons.Audio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatFileSize(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(NextIcons.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(NextIcons.Play, contentDescription = null) },
                        onClick = { showMenu = false; onPlay() },
                    )
                    DropdownMenuItem(
                        text = { Text("Restore to Gallery") },
                        leadingIcon = { Icon(NextIcons.LockOpen, contentDescription = null) },
                        onClick = { showMenu = false; onRestore() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Permanently") },
                        leadingIcon = { Icon(NextIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; showDeleteConfirm = true },
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Permanently") },
            text = { Text("\"${file.name}\" will be permanently deleted and cannot be recovered. Are you sure?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

// ─── Custom MediaStore Picker Dialog ──────────────────────────────────────────

private data class MediaStoreItem(val id: Long, val uri: Uri, val displayName: String, val sizeMb: String)

@Composable
fun MediaStorePickerDialog(
    mediaType: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    var mediaItems by remember { mutableStateOf<List<MediaStoreItem>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(mediaType) {
        isLoading = true
        mediaItems = queryMediaStoreItems(context, mediaType)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (mediaItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No ${if (mediaType == "video") "videos" else "music"} found on device.")
                }
            } else {
                Column {
                    Text(
                        "${selectedIds.size} of ${mediaItems.size} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(mediaItems, key = { it.id }) { item ->
                            val isSelected = item.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.displayName,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        item.sizeMb,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val uris = mediaItems.filter { it.id in selectedIds }.map { it.uri }
                    onConfirm(uris)
                },
                enabled = selectedIds.isNotEmpty(),
            ) { Text("Add (${selectedIds.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun queryMediaStoreItems(context: Context, mediaType: String): List<MediaStoreItem> {
    val collection = when (mediaType) {
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
    )
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    val result = mutableListOf<MediaStoreItem>()
    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val name = cursor.getString(nameCol) ?: "Unknown"
            val size = cursor.getLong(sizeCol)
            val uri = ContentUris.withAppendedId(collection, id)
            val sizeMb = if (size > 0) "%.1f MB".format(size / 1_048_576.0) else ""
            result.add(MediaStoreItem(id, uri, name, sizeMb))
        }
    }
    return result
}
