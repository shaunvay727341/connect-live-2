package com.example.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Storage Access Framework (SAF) folder manager for persistent user-selected storage.
 * Creates and maintains ConnectLive/{Images,Videos,VoiceNotes,Backups} with zero restricted storage permissions.
 */
object SafMediaManager {

    private const val TAG = "SafMediaManager"
    const val SUB_IMAGES = "Images"
    const val SUB_VIDEOS = "Videos"
    const val SUB_VOICE_NOTES = "VoiceNotes"
    const val SUB_BACKUPS = "Backups"

    fun hasPersistedFolderAccess(context: Context, safTreeUri: String): Boolean {
        if (safTreeUri.isBlank()) return false
        return runCatching {
            val uri = Uri.parse(safTreeUri)
            val persisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (!persisted) return@runCatching false
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.exists() && doc.canWrite()
        }.getOrDefault(false)
    }

    fun persistTreePermission(context: Context, treeUri: Uri): Result<String> {
        return runCatching {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            ensureFolderStructure(context, treeUri)
            treeUri.toString()
        }
    }

    fun getFolderDisplayName(context: Context, safTreeUri: String): String {
        if (safTreeUri.isBlank()) return "Not selected"
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri))?.name ?: "ConnectLive Folder"
        }.getOrDefault("ConnectLive Folder")
    }

    fun ensureFolderStructure(context: Context, treeUri: Uri) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
            listOf(SUB_IMAGES, SUB_VIDEOS, SUB_VOICE_NOTES, SUB_BACKUPS).forEach { folderName ->
                if (root.findFile(folderName) == null) {
                    root.createDirectory(folderName)
                }
            }
        }
    }

    private fun getOrCreateSubfolder(context: Context, safTreeUri: String, subfolder: String): DocumentFile? {
        if (safTreeUri.isBlank()) return null
        return runCatching {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri)) ?: return null
            root.findFile(subfolder) ?: root.createDirectory(subfolder)
        }.getOrNull()
    }

    suspend fun copyUriToSafFolder(
        context: Context,
        safTreeUri: String,
        subfolder: String,
        sourceUri: Uri,
        fileName: String,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        if (safTreeUri.isBlank()) return@withContext null
        runCatching {
            val folder = getOrCreateSubfolder(context, safTreeUri, subfolder) ?: return@withContext null
            folder.findFile(fileName)?.delete()
            val targetDoc = folder.createFile(mimeType, fileName) ?: return@withContext null
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            targetDoc.uri.toString()
        }.onFailure {
            Log.w(TAG, "Unable to mirror $fileName to SAF $subfolder", it)
        }.getOrNull()
    }

    suspend fun saveBytesToSafAndLocal(
        context: Context,
        safTreeUri: String,
        subfolder: String,
        localSubdir: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Pair<File, String?> = withContext(Dispatchers.IO) {
        val localDir = File(context.filesDir, localSubdir).apply { mkdirs() }
        val localFile = File(localDir, fileName)
        localFile.writeBytes(bytes)

        val safUri = if (safTreeUri.isNotBlank()) {
            runCatching {
                val folder = getOrCreateSubfolder(context, safTreeUri, subfolder)
                if (folder != null) {
                    folder.findFile(fileName)?.delete()
                    val doc = folder.createFile(mimeType, fileName)
                    if (doc != null) {
                        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                            out.write(bytes)
                        }
                        doc.uri.toString()
                    } else null
                } else null
            }.getOrNull()
        } else null

        localFile to safUri
    }

    suspend fun backupConversationMessages(
        context: Context,
        safTreeUri: String,
        conversationId: String,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().apply {
                put("conversationId", conversationId)
                put("exportedAt", System.currentTimeMillis())
                put("count", messages.size)
                val arr = JSONArray()
                messages.forEach { msg ->
                    arr.put(JSONObject().apply {
                        put("id", msg.id)
                        put("senderId", msg.senderId)
                        put("senderName", msg.senderName)
                        put("messageType", msg.messageType)
                        put("text", msg.text)
                        put("imageUrl", msg.imageUrl)
                        put("videoUrl", msg.videoUrl)
                        put("voiceUrl", msg.voiceUrl)
                        put("voiceDurationMs", msg.voiceDurationMs)
                        put("timestampMs", msg.createdAt?.toDate()?.time ?: 0L)
                    })
                }
                put("messages", arr)
            }.toString(2)

            val fileName = "chat_backup_${conversationId}.json"
            val internalBackup = File(File(context.filesDir, "backups").apply { mkdirs() }, fileName)
            internalBackup.writeText(json)

            if (safTreeUri.isNotBlank()) {
                val folder = getOrCreateSubfolder(context, safTreeUri, SUB_BACKUPS)
                if (folder != null) {
                    folder.findFile(fileName)?.delete()
                    val doc = folder.createFile("application/json", fileName)
                    if (doc != null) {
                        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                            out.write(json.toByteArray())
                        }
                        return@runCatching "Backed up ${messages.size} messages to folder/$SUB_BACKUPS/$fileName"
                    }
                }
            }
            "Backed up ${messages.size} messages internally"
        }
    }

    suspend fun countTransferredFiles(context: Context, safTreeUri: String): Map<String, Int> = withContext(Dispatchers.IO) {
        if (safTreeUri.isBlank()) return@withContext emptyMap()
        runCatching {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri)) ?: return@withContext emptyMap()
            listOf(SUB_IMAGES, SUB_VIDEOS, SUB_VOICE_NOTES, SUB_BACKUPS).associateWith { name ->
                root.findFile(name)?.listFiles()?.size ?: 0
            }
        }.getOrDefault(emptyMap())
    }
}
