package com.example.data.turn

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.IceServerDto
import com.example.data.model.TurnBackendResponseDto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit

data class IceConfigResult(
    val iceServers: List<PeerConnection.IceServer>,
    val turnRelayEnabled: Boolean,
    val statusLabel: String,
    val warningMessage: String? = null
)

class TurnCredentialsService {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val wrappedAdapter = moshi.adapter(TurnBackendResponseDto::class.java)
    private val listType = Types.newParameterizedType(List::class.java, IceServerDto::class.java)
    private val listAdapter = moshi.adapter<List<IceServerDto>>(listType)

    suspend fun fetchIceServers(): IceConfigResult = withContext(Dispatchers.IO) {
        val backendUrl = BuildConfig.TURN_BACKEND_URL.trim()
        if (backendUrl.isBlank() || backendUrl == "UNSET" || backendUrl.contains("example.com")) {
            return@withContext IceConfigResult(
                iceServers = defaultStunServers(),
                turnRelayEnabled = false,
                statusLabel = "STUN P2P",
                warningMessage = "TURN backend URL not set; using STUN direct P2P fallback."
            )
        }

        try {
            val currentUser = Firebase.auth.currentUser
                ?: throw IllegalStateException("Authentication required to request TURN credentials.")
            val idToken = currentUser.getIdToken(false).await().token
                ?: throw IllegalStateException("Failed to obtain Firebase ID token.")

            val request = Request.Builder()
                .url(backendUrl)
                .header("Authorization", "Bearer $idToken")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("TURN backend returned HTTP ${response.code}")
                }

                val dtoList = parseIceServerDtos(bodyString)
                val mapped = mapDtosToIceServers(dtoList)
                if (mapped.isEmpty()) {
                    throw IllegalStateException("TURN backend returned zero ICE servers.")
                }

                val hasTurn = mapped.any { server ->
                    server.urls.any { it.startsWith("turn:", ignoreCase = true) || it.startsWith("turns:", ignoreCase = true) }
                }

                IceConfigResult(
                    iceServers = mapped,
                    turnRelayEnabled = hasTurn,
                    statusLabel = if (hasTurn) "Metered TURN Relay" else "STUN P2P",
                    warningMessage = null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to STUN ICE servers: ${e.message}")
            IceConfigResult(
                iceServers = defaultStunServers(),
                turnRelayEnabled = false,
                statusLabel = "STUN Fallback",
                warningMessage = "TURN backend unavailable (${e.localizedMessage}); using STUN fallback."
            )
        }
    }

    private fun parseIceServerDtos(json: String): List<IceServerDto> {
        val trimmed = json.trim()
        return if (trimmed.startsWith("[")) {
            listAdapter.fromJson(trimmed).orEmpty()
        } else {
            wrappedAdapter.fromJson(trimmed)?.iceServers.orEmpty()
        }
    }

    private fun mapDtosToIceServers(dtos: List<IceServerDto>): List<PeerConnection.IceServer> {
        return dtos.mapNotNull { dto ->
            val urls: List<String> = when (val raw = dto.urls) {
                is String -> listOf(raw).filter { it.isNotBlank() }
                is List<*> -> raw.mapNotNull { it as? String }.filter { it.isNotBlank() }
                else -> emptyList()
            }
            if (urls.isEmpty()) return@mapNotNull null

            val builder = PeerConnection.IceServer.builder(urls)
            if (!dto.username.isNullOrBlank()) {
                builder.setUsername(dto.username)
            }
            if (!dto.credential.isNullOrBlank()) {
                builder.setPassword(dto.credential)
            }
            builder.createIceServer()
        }
    }

    companion object {
        private const val TAG = "TurnService"

        fun defaultStunServers(): List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer()
        )
    }
}
