package com.shsai.android

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var downloadLayout: View
    private lateinit var chatLayout: View
    private lateinit var downloadStatus: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadBtn: MaterialButton
    private lateinit var messagesRv: RecyclerView
    private lateinit var userInputEt: EditText
    private lateinit var sendFab: FloatingActionButton

    private lateinit var engine: InferenceEngine
    private var generationJob: Job? = null
    private var isModelReady = false
    private var isDownloading = false

    private val messages = mutableListOf<Message>()
    private val messageAdapter = MessageAdapter(messages)

    private val modelFileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"
    private val modelUrl =
        "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"

    private val systemPrompt = "You are SHS AI, a helpful, friendly, and intelligent AI assistant. Answer questions clearly and concisely."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        onBackPressedDispatcher.addCallback { }

        downloadLayout = findViewById(R.id.download_layout)
        chatLayout = findViewById(R.id.chat_layout)
        downloadStatus = findViewById(R.id.download_status)
        downloadProgress = findViewById(R.id.download_progress)
        downloadBtn = findViewById(R.id.download_btn)
        messagesRv = findViewById(R.id.messages)
        userInputEt = findViewById(R.id.user_input)
        sendFab = findViewById(R.id.fab)

        messagesRv.layoutManager = LinearLayoutManager(this)
        messagesRv.adapter = messageAdapter

        sendFab.setOnClickListener { handleUserInput() }
        downloadBtn.setOnClickListener { if (!isDownloading) startDownload() }

        lifecycleScope.launch(Dispatchers.Default) {
            engine = AiChat.getInferenceEngine(applicationContext)
        }

        val modelFile = File(filesDir, modelFileName)
        if (modelFile.exists() && modelFile.length() > 100_000_000L) {
            showChatScreen()
            loadModel(modelFile.absolutePath)
        } else {
            showDownloadScreen()
        }
    }

    private fun showDownloadScreen() {
        downloadLayout.visibility = View.VISIBLE
        chatLayout.visibility = View.GONE
    }

    private fun showChatScreen() {
        downloadLayout.visibility = View.GONE
        chatLayout.visibility = View.VISIBLE
    }

    private fun startDownload() {
        isDownloading = true
        downloadBtn.isEnabled = false
        downloadStatus.text = "Connecting to server…"
        downloadProgress.visibility = View.VISIBLE
        downloadProgress.progress = 0

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelFile = File(filesDir, modelFileName)
                val tmpFile = File(filesDir, "$modelFileName.tmp")

                val connection = URL(modelUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
                            val downloadedMb = downloadedBytes / 1_048_576
                            val totalMb = if (totalBytes > 0) totalBytes / 1_048_576 else 0
                            withContext(Dispatchers.Main) {
                                downloadProgress.progress = progress
                                downloadStatus.text = "Downloading… $downloadedMb MB / $totalMb MB"
                            }
                        }
                    }
                }

                tmpFile.renameTo(modelFile)

                withContext(Dispatchers.Main) {
                    downloadStatus.text = "Download complete! Loading SHS AI…"
                    showChatScreen()
                    loadModel(modelFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    downloadBtn.isEnabled = true
                    downloadProgress.visibility = View.GONE
                    downloadStatus.text = "Download failed. Check your internet and try again.\n\n${e.message}"
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadModel(path: String) {
        userInputEt.hint = getString(R.string.model_loading)
        userInputEt.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                engine.loadModel(path)
                engine.setSystemPrompt(systemPrompt)
                withContext(Dispatchers.Main) {
                    isModelReady = true
                    userInputEt.hint = getString(R.string.chat_hint)
                    userInputEt.isEnabled = true
                    addAiMessage(getString(R.string.model_ready))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed", e)
                withContext(Dispatchers.Main) {
                    userInputEt.hint = "Failed to load model: ${e.message}"
                    Toast.makeText(this@MainActivity, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleUserInput() {
        val input = userInputEt.text.toString().trim()
        if (input.isEmpty() || !isModelReady) return

        if (generationJob?.isActive == true) {
            generationJob?.cancel()
            return
        }

        userInputEt.text.clear()
        addUserMessage(input)

        val aiIndex = messages.size
        messages.add(Message(role = "assistant", content = "…"))
        messageAdapter.notifyItemInserted(aiIndex)
        messagesRv.scrollToPosition(messages.size - 1)

        val sb = StringBuilder()

        generationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                engine.sendUserPrompt(input).collect { token ->
                    sb.append(token)
                    withContext(Dispatchers.Main) {
                        messages[aiIndex] = Message(role = "assistant", content = sb.toString())
                        messageAdapter.notifyItemChanged(aiIndex)
                        messagesRv.scrollToPosition(messages.size - 1)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                withContext(Dispatchers.Main) {
                    messages[aiIndex] = Message(role = "assistant", content = "Error: ${e.message}")
                    messageAdapter.notifyItemChanged(aiIndex)
                }
            }
        }
    }

    private fun addUserMessage(text: String) {
        messages.add(Message(role = "user", content = text))
        messageAdapter.notifyItemInserted(messages.size - 1)
        messagesRv.scrollToPosition(messages.size - 1)
    }

    private fun addAiMessage(text: String) {
        messages.add(Message(role = "assistant", content = text))
        messageAdapter.notifyItemInserted(messages.size - 1)
        messagesRv.scrollToPosition(messages.size - 1)
    }

    override fun onDestroy() {
        generationJob?.cancel()
        if (::engine.isInitialized) engine.destroy()
        super.onDestroy()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}

data class Message(val role: String, val content: String)
