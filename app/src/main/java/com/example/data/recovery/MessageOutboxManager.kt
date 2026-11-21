package com.example.data.recovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.repository.ConnectLiveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PendingOutboxMessage(
    val localId: String,
    val conversationId: String,
    val participantIds: List<String>,
    val senderName: String,
    val senderPhoto: String,
    val text: String,
    val messageType: String,
    val imageUrl: String = "",
    val videoUrl: String = "",
    val videoThumbUrl: String = "",
    val videoDurationMs: Long = 0L,
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSenderName: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Durable offline-first outbox queue that preserves messages across network drops and
 * automatically recovers/delivers them when connectivity returns.
 */
class MessageOutboxManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()
    private val flushMutex = Mutex()
    private val outboxFile = File(appContext.filesDir, "connect_live_outbox.json")

    private val _pendingMessages = MutableStateFlow<List<PendingOutboxMessage>>(emptyList())
    val pendingMessages: StateFlow<List<PendingOutboxMessage>> = _pendingMessages.asStateFlow()

    private val _isNetworkOnline = MutableStateFlow(checkCurrentlyOnline())
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()

    private var callbackRegistered = false

    init {
        scope.launch {
            _pendingMessages.value = readFromDisk()
            registerConnectivityMonitor()
        }
    }

    fun checkCurrentlyOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerConnectivityMonitor() {
        if (callbackRegistered) return
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkOnline.value = true
                    flushAllPending()
                }

                override fun onLost(network: Network) {
                    _isNetworkOnline.value = checkCurrentlyOnline()
                }
            })
            callbackRegistered = true
        }
    }

    suspend fun enqueueMessage(item: PendingOutboxMessage) {
        fileMutex.withLock {
            val current = readFromDisk().toMutableList()
            if (current.none { it.localId == item.localId }) {
                current += item
                writeToDisk(current)
                _pendingMessages.value = current
            }
        }
    }

    suspend fun removeMessage(localId: String) {
        fileMutex.withLock {
            val updated = readFromDisk().filterNot { it.localId == localId }
            writeToDisk(updated)
            _pendingMessages.value = updated
        }
    }

    fun flushAllPending() {
        scope.launch {
            flushMutex.withLock {
                if (!checkCurrentlyOnline()) return@withLock
                val repo = ConnectLiveRepository(appContext)
                val queue = fileMutex.withLock { readFromDisk() }
                for (item in queue) {
                    val res = repo.sendMessage(
                        conversationId = item.conversationId,
                        participantIds = item.participantIds,
                        senderName = item.senderName,
                        text = item.text,
                        messageType = MessageType.fromRaw(item.messageType),
                        imageUrl = item.imageUrl,
                        videoUrl = item.videoUrl,
                        videoThumbUrl = item.videoThumbUrl,
                        videoDurationMs = item.videoDurationMs,
                        deliveryStatus = DeliveryStatus.SENT,
                        replyToId = item.replyToId,
                        replyToText = item.replyToText,
                        replyToSenderName = item.replyToSenderName,
                        senderPhoto = item.senderPhoto
                    )
                    if (res.isSuccess) {
                        removeMessage(item.localId)
                        Log.i(TAG, "Recovered & delivered queued message ${item.localId}")
                    } else {
                        val err = res.exceptionOrNull()?.message.orEmpty()
                        if (err.contains("limit", ignoreCase = true) || err.contains("Invalid", ignoreCase = true)) {
                            removeMessage(item.localId)
                        }
                    }
                }
            }
        }
    }

    fun newLocalId(): String = "outbox_${UUID.randomUUID().toString().replace("-", "").take(14)}"

    private fun readFromDisk(): List<PendingOutboxMessage> {
        if (!outboxFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(outboxFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val partsArr = obj.getJSONArray("participantIds")
                    val parts = buildList {
                        for (j in 0 until partsArr.length()) add(partsArr.getString(j))
                    }
                    add(
                        PendingOutboxMessage(
                            localId = obj.getString("localId"),
                            conversationId = obj.getString("conversationId"),
                            participantIds = parts,
                            senderName = obj.getString("senderName"),
                            senderPhoto = obj.optString("senderPhoto"),
                            text = obj.optString("text"),
                            messageType = obj.optString("messageType", MessageType.TEXT.name),
                            imageUrl = obj.optString("imageUrl"),
                            videoUrl = obj.optString("videoUrl"),
                            videoThumbUrl = obj.optString("videoThumbUrl"),
                            videoDurationMs = obj.optLong("videoDurationMs"),
                            replyToId = obj.optString("replyToId"),
                            replyToText = obj.optString("replyToText"),
                            replyToSenderName = obj.optString("replyToSenderName"),
                            createdAtMs = obj.optLong("createdAtMs")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeToDisk(list: List<PendingOutboxMessage>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { item ->
                arr.put(JSONObject().apply {
                    put("localId", item.localId)
                    put("conversationId", item.conversationId)
                    put("participantIds", JSONArray(item.participantIds))
                    put("senderName", item.senderName)
                    put("senderPhoto", item.senderPhoto)
                    put("text", item.text)
                    put("messageType", item.messageType)
                    put("imageUrl", item.imageUrl)
                    put("videoUrl", item.videoUrl)
                    put("videoThumbUrl", item.videoThumbUrl)
                    put("videoDurationMs", item.videoDurationMs)
                    put("replyToId", item.replyToId)
                    put("replyToText", item.replyToText)
                    put("replyToSenderName", item.replyToSenderName)
                    put("createdAtMs", item.createdAtMs)
                })
            }
            outboxFile.writeText(arr.toString())
        }
    }

    companion object {
        private const val TAG = "MessageOutboxManager"

        @Volatile
        private var instance: MessageOutboxManager? = null

        fun getInstance(context: Context): MessageOutboxManager {
            return instance ?: synchronized(this) {
                instance ?: MessageOutboxManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
