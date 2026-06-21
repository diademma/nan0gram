package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================================
// 1. СУЩНОСТИ (Таблицы базы данных)
// ============================================================================

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val unreadCount: Int,
    val lastMessageTime: Long,
    val lastMessagePreview: String,
    val isPinned: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["chatId"]), Index(value = ["timestamp"])] // Индексы для сверхбыстрой выборки и сортировки
)
data class MessageEntity(
    @PrimaryKey val msgId: String,
    val chatId: String,
    val type: String, // "in" (входящее) или "out" (исходящее)
    val author: String,
    val text: String,
    val timestamp: Long,
    val mediaType: String, // "none", "photo", "video", "voice", "file"
    val mediaPaths: String, // Строка в формате JSON-массива: ["/data/user/0/.../cache/1.jpg"]
    val mediaThumbnails: String, // Строка в формате JSON-массива для превью видео
    val fileName: String,
    val fileSize: Long,
    val audioDuration: Int,
    val replyToId: String,
    val reaction: String
)

// ============================================================================
// 2. DATA ACCESS OBJECT (Запросы к БД)
// ============================================================================

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY isPinned DESC, lastMessageTime DESC")
    suspend fun getAllChats(): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: MessageEntity)

    // Запрашиваем кусок сообщений для пагинации (бесконечный скролл)
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessages(chatId: String, limit: Int, offset: Int): List<MessageEntity>

    // Умная очистка: оставляет последние keepCount сообщений, остальное удаляет
    @Query("""
        DELETE FROM messages 
        WHERE chatId = :chatId 
        AND msgId NOT IN (
            SELECT msgId FROM messages 
            WHERE chatId = :chatId 
            ORDER BY timestamp DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun deleteOldMessages(chatId: String, keepCount: Int)

    // Тотальная зачистка переписок
    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
    
    // Запрос для очистки медиа (извлекает все сохраненные пути)
    @Query("SELECT mediaPaths FROM messages WHERE mediaPaths != '' AND mediaPaths != '[]'")
    suspend fun getAllMediaPaths(): List<String>

    // Удаление конкретного сообщения
    @Query("DELETE FROM messages WHERE chatId = :chatId AND msgId = :msgId")
    suspend fun deleteMessage(chatId: String, msgId: String)

    // Обновление реакции
    @Query("UPDATE messages SET reaction = :reaction WHERE chatId = :chatId AND msgId = :msgId")
    suspend fun updateReaction(chatId: String, msgId: String, reaction: String)
}

// ============================================================================
// 3. ИНИЦИАЛИЗАЦИЯ БАЗЫ ДАННЫХ
// ============================================================================

@Database(entities = [ChatEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nan0gram_database"
                )
                .fallbackToDestructiveMigration() // При смене структуры БД старые данные стираются без крэша
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ============================================================================
// 4. РЕПОЗИТОРИЙ (Обертка для безопасного вызова и логирования)
// ============================================================================

class NanogramRepository(
    private val db: AppDatabase,
    private val log: (String) -> Unit
) {
    private val dao = db.chatDao()

    suspend fun saveChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        try {
            dao.insertChat(chat)
            log("[DB] Сохранен/обновлен чат: ${chat.name}")
        } catch (e: Exception) {
            log("[DB Error] Ошибка сохранения чата: ${e.message}")
        }
    }

    suspend fun saveMessage(msg: MessageEntity) = withContext(Dispatchers.IO) {
        try {
            dao.insertMessage(msg)
            log("[DB] Сохранено сообщение в чат ${msg.chatId} [ID: ${msg.msgId}]")
        } catch (e: Exception) {
            log("[DB Error] Ошибка сохранения сообщения: ${e.message}")
        }
    }

    suspend fun getMessages(chatId: String, limit: Int, offset: Int): List<MessageEntity> = withContext(Dispatchers.IO) {
        try {
            val msgs = dao.getMessages(chatId, limit, offset)
            log("[DB] Загружено ${msgs.size} сообщений для чата $chatId (Смещение: $offset)")
            msgs
        } catch (e: Exception) {
            log("[DB Error] Ошибка загрузки сообщений: ${e.message}")
            emptyList()
        }
    }

    suspend fun getChats(): List<ChatEntity> = withContext(Dispatchers.IO) {
        try {
            val chats = dao.getAllChats()
            log("[DB] Загружен список чатов (${chats.size} контактов)")
            chats
        } catch (e: Exception) {
            log("[DB Error] Ошибка загрузки чатов: ${e.message}")
            emptyList()
        }
    }

    // Удаляет переписку, оставляя последние N сообщений в чате
    suspend fun pruneChatMessages(chatId: String, keepCount: Int = 100) = withContext(Dispatchers.IO) {
        try {
            dao.deleteOldMessages(chatId, keepCount)
            log("[DB] Ротация истории в чате $chatId завершена (Оставлено: $keepCount)")
        } catch (e: Exception) {
            log("[DB Error] Ошибка ротации сообщений: ${e.message}")
        }
    }

    // Полная очистка всей базы переписок (кнопка в настройках)
    suspend fun clearAllHistoryLog() = withContext(Dispatchers.IO) {
        try {
            dao.clearAllMessages()
            log("[DB] ВНИМАНИЕ: История всех переписок очищена пользователем!")
        } catch (e: Exception) {
            log("[DB Error] Ошибка полной очистки переписок: ${e.message}")
        }
    }

    // Удаление конкретного сообщения
    suspend fun deleteMessage(chatId: String, msgId: String) = withContext(Dispatchers.IO) {
        try {
            dao.deleteMessage(chatId, msgId)
            log("[DB] Удалено сообщение [ID: $msgId] из чата $chatId")
        } catch (e: Exception) {
            log("[DB Error] Ошибка удаления сообщения: ${e.message}")
        }
    }

    // Обновление реакции сообщения
    suspend fun updateReaction(chatId: String, msgId: String, reaction: String) = withContext(Dispatchers.IO) {
        try {
            dao.updateReaction(chatId, msgId, reaction)
            log("[DB] Обновлена реакция ($reaction) для сообщения $msgId в чате $chatId")
        } catch (e: Exception) {
            log("[DB Error] Ошибка обновления реакции: ${e.message}")
        }
    }
}
