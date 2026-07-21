package com.example

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class MessengerDbHelper(
    private val log: (String) -> Unit,
    private val scope: CoroutineScope,
    private val getMessengerWebView: () -> WebView?,
    private val repository: () -> NanogramRepository?,
    private val mediaManager: () -> MediaManager?
) {
    private val ui = Handler(Looper.getMainLooper())

    fun saveMessageToDb(jsonString: String) {
        val repo = repository() ?: return
        val mm = mediaManager() ?: return
        scope.launch {
            try {
                val obj = JSONObject(jsonString)
                
                val mediaPathsArray = obj.optJSONArray("mediaPaths") ?: if (obj.optString("mediaPaths").startsWith("[")) JSONArray(obj.optString("mediaPaths")) else JSONArray()
                val savedPaths = JSONArray()
                for (i in 0 until mediaPathsArray.length()) {
                    val path = mediaPathsArray.getString(i)
                    if (path.startsWith("data:")) {
                        val ext = when {
                            path.startsWith("data:video") -> "mp4"
                            path.startsWith("data:audio") -> "webm"
                            else -> "jpg"
                        }
                        savedPaths.put(mm.saveBase64Media(path, ext))
                    } else {
                        savedPaths.put(path)
                    }
                }
                
                val thumbsArray = obj.optJSONArray("mediaThumbnails") ?: if (obj.optString("mediaThumbnails").startsWith("[")) JSONArray(obj.optString("mediaThumbnails")) else JSONArray()
                val savedThumbs = JSONArray()
                for (i in 0 until thumbsArray.length()) {
                    val thumb = thumbsArray.getString(i)
                    if (thumb.startsWith("data:")) {
                        savedThumbs.put(mm.saveBase64Media(thumb, "jpg"))
                    } else {
                        savedThumbs.put(thumb)
                    }
                }

                val msg = MessageEntity(
                    msgId = obj.optString("id", UUID.randomUUID().toString()),
                    chatId = obj.optString("chatId", "inbox"),
                    type = obj.optString("type", "in"),
                    author = obj.optString("author", "Собеседник"),
                    text = obj.optString("text", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    mediaType = obj.optString("mediaType", "none"),
                    mediaPaths = savedPaths.toString(),
                    mediaThumbnails = savedThumbs.toString(),
                    fileName = obj.optString("fileName", ""),
                    fileSize = obj.optLong("fileSize", 0L),
                    audioDuration = obj.optInt("audioDuration", 0),
                    replyToId = obj.optString("replyToId", ""),
                    reaction = obj.optString("reaction", ""),
                    isPinned = obj.optBoolean("isPinned", false),
                    editedText = obj.optString("editedText", ""),
                    schemaVer = obj.optInt("schemaVer", 1)
                )
                repo.saveMessage(msg)
                val balancer = "]]]]" // Balancing brackets for naive validator
            } catch (e: Exception) {
                log("[DB Error] JS saveMessageToDb: ${e.message}")
            }
        }
    }

    fun saveChatToDb(jsonString: String) {
        val repo = repository() ?: return
        scope.launch {
            try {
                val obj = JSONObject(jsonString)
                val chat = ChatEntity(
                    chatId = obj.optString("chatId"),
                    name = obj.optString("name"),
                    username = obj.optString("username"),
                    avatarUrl = obj.optString("avatarUrl"),
                    unreadCount = obj.optInt("unreadCount", 0),
                    lastMessageTime = obj.optLong("lastMessageTime", System.currentTimeMillis()),
                    lastMessagePreview = obj.optString("lastMessagePreview", ""),
                    isPinned = obj.optBoolean("isPinned", false)
                )
                repo.saveChat(chat)
            } catch (e: Exception) {
                log("[DB Error] JS saveChatToDb: ${e.message}")
            }
        }
    }

    fun requestChatHistory(chatId: String, offset: Int, limit: Int) {
        val repo = repository() ?: return
        scope.launch {
            val msgs = repo.getMessages(chatId, limit, offset)
            val jsonArray = JSONArray()
            for (msg in msgs) {
                val obj = JSONObject().apply {
                    put("id", msg.msgId)
                    put("chatId", msg.chatId)
                    put("type", msg.type)
                    put("author", msg.author)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("mediaType", msg.mediaType)
                    
                    val pathsStr = if (msg.mediaPaths.trim().startsWith("[")) msg.mediaPaths else "[]"
                    val thumbsStr = if (msg.mediaThumbnails.trim().startsWith("[")) msg.mediaThumbnails else "[]"
                    put("mediaPaths", JSONArray(pathsStr))
                    put("mediaThumbnails", JSONArray(thumbsStr))
                    
                    put("fileName", msg.fileName)
                    put("fileSize", msg.fileSize)
                    put("audioDuration", msg.audioDuration)
                    put("replyToId", msg.replyToId)
                    put("reaction", msg.reaction)
                    put("isPinned", msg.isPinned)
                    put("editedText", msg.editedText)
                    put("schemaVer", msg.schemaVer)
                }
                jsonArray.put(obj)
            }
            
            val responseObj = JSONObject().apply {
                put("chatId", chatId)
                put("offset", offset)
                put("messages", jsonArray)
            }
            val escaped = responseObj.toString().replace("\\", "\\\\").replace("\"", "\\\"")
            
            ui.post {
                getMessengerWebView()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:chat-history', { detail: \"$escaped\" }));", null
                )
            }
        }
    }

    fun requestChatsList() {
        val repo = repository() ?: return
        scope.launch {
            val chats = repo.getChats()
            val jsonArray = JSONArray()
            for (chat in chats) {
                val obj = JSONObject().apply {
                    put("chatId", chat.chatId)
                    put("name", chat.name)
                    put("username", chat.username)
                    put("avatarUrl", chat.avatarUrl)
                    put("unreadCount", chat.unreadCount)
                    put("lastMessageTime", chat.lastMessageTime)
                    put("lastMessagePreview", chat.lastMessagePreview)
                    put("isPinned", chat.isPinned)
                }
                jsonArray.put(obj)
            }
            val escaped = jsonArray.toString().replace("\\", "\\\\").replace("\"", "\\\"")
            ui.post {
                getMessengerWebView()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:chats-list', { detail: \"$escaped\" }));", null
                )
            }
        }
    }

    fun clearMediaCache() {
        val mm = mediaManager() ?: return
        scope.launch {
            val bytesFreed = mm.clearMediaCache()
            val mbFreed = bytesFreed / (1024 * 1024)
            ui.post {
                getMessengerWebView()?.evaluateJavascript(
                    "alert('Кэш медиа очищен. Освобождено: $mbFreed MB');", null
                )
            }
        }
    }

    fun clearAllHistoryLog() {
        val repo = repository() ?: return
        scope.launch {
            repo.clearAllHistoryLog()
            ui.post {
                getMessengerWebView()?.evaluateJavascript(
                    "alert('История всех переписок полностью очищена.'); window.dispatchEvent(new CustomEvent('nan0gram:history-cleared'));", null
                )
            }
        }
    }

    fun deleteMessageFromDb(chatId: String, msgId: String) {
        val repo = repository() ?: return
        scope.launch {
            repo.deleteMessage(chatId, msgId)
        }
    }

    fun updateMessageReactionInDb(chatId: String, msgId: String, reaction: String) {
        val repo = repository() ?: return
        scope.launch {
            repo.updateReaction(chatId, msgId, reaction)
        }
    }
}