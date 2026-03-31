# ClawChat API Reference

## 公共 API

### 1. ViewModel APIs

#### MainViewModel
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val sessionRepository: SessionRepository,
    private val encryptedStorage: EncryptedStorage
) : ViewModel()
```

**公共属性**:
- `uiState: StateFlow<MainUiState>` - 主界面 UI 状态
- `isPaired: StateFlow<Boolean>` - 配对状态
- `events: Flow<UiEvent>` - UI 事件流

**公共方法**:
- `fun connect(gatewayUrl: String)` - 连接 Gateway
- `fun disconnect()` - 断开连接
- `fun clearError()` - 清除错误状态
- `fun selectSession(sessionId: String)` - 选择会话
- `fun refreshSessions()` - 刷新会话列表
- `fun createSession()` - 创建新会话
- `fun deleteSession(sessionId: String)` - 删除会话

#### SessionViewModel
```kotlin
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val gateway: GatewayConnection,
    private val messageRepository: MessageRepository
) : ViewModel()
```

**公共属性**:
- `state: StateFlow<SessionUiState>` - 会话 UI 状态

**公共方法**:
- `fun sendMessage(message: String)` - 发送消息
- `fun continueGeneration()` - 继续生成
- `fun retryMessage(messageId: String)` - 重试消息
- `fun deleteMessage(messageId: String)` - 删除消息
- `fun clearError()` - 清除错误

### 2. Repository APIs

#### SessionRepository
```kotlin
interface SessionRepository {
    fun getAllSessions(): Flow<List<Session>>
    fun getSessionById(id: String): Flow<Session?>
    suspend fun insertSession(session: Session)
    suspend fun deleteSession(id: String)
    suspend fun updateSession(session: Session)
}
```

#### MessageRepository
```kotlin
interface MessageRepository {
    fun getMessagesForSession(sessionId: String): Flow<List<Message>>
    suspend fun insertMessage(message: Message)
    suspend fun deleteMessage(id: String)
    suspend fun deleteMessagesForSession(sessionId: String)
}
```

### 3. Util APIs

#### DateTimeUtils
```kotlin
object DateTimeUtils {
    fun formatRelativeTime(timestamp: Long, now: Long): String
    fun formatSessionGroupLabel(timestamp: Long): String
    fun formatTime(timestamp: Long): String
    fun formatDate(timestamp: Long): String
    fun formatDateTime(timestamp: Long): String
    fun formatFullDateTime(timestamp: Long): String
}
```

#### StringUtils
```kotlin
object StringUtils {
    fun isBlank(str: String?): Boolean
    fun isNotBlank(str: String?): Boolean
    fun truncate(str: String?, maxLength: Int, ellipsis: String): String
    fun extractBase64FromDataUrl(dataUrl: String): String
    fun isValidUrl(str: String?): Boolean
    fun isValidJson(str: String?): Boolean
    fun removeProtocol(url: String?): String
    fun addProtocol(url: String?, protocol: String): String
    fun formatFileSize(bytes: Long): String
}
```

#### ValidationUtils
```kotlin
object ValidationUtils {
    fun isValidEmail(email: String?): Boolean
    fun isValidPhone(phone: String?): Boolean
    fun isValidIp(ip: String?): Boolean
    fun isValidPort(port: String?): Boolean
    fun isValidGatewayAddress(address: String?): Boolean
    fun isValidToken(token: String?): Boolean
    fun isValidMessageContent(content: String?): Boolean
    fun isValidFileSize(bytes: Long, maxSizeMB: Int): Boolean
}
```

#### CollectionUtils
```kotlin
object CollectionUtils {
    fun <T> isEmpty(collection: Collection<T>?): Boolean
    fun <T> isNotEmpty(collection: Collection<T>?): Boolean
    fun <T> getOrNull(list: List<T>?, index: Int, defaultValue: T?): T?
    fun <T> chunked(list: List<T>, batchSize: Int): List<List<T>>
    fun <T> distinct(list: List<T>): List<T>
    fun <T> filterNotNull(list: List<T?>): List<T>
    fun <T> joinToString(collection: Collection<T>?, separator: String): String
}
```

### 4. Network APIs

#### GatewayConnection
```kotlin
interface GatewayConnection {
    val connectionState: StateFlow<WebSocketConnectionState>
    
    suspend fun connect(url: String, token: String?): Result<Unit>
    fun disconnect()
    suspend fun call(method: String, params: Map<String, Any?>): Result<JsonObject>
}
```

#### WebSocketConnectionState
```kotlin
sealed class WebSocketConnectionState {
    object Disconnected : WebSocketConnectionState()
    object Connecting : WebSocketConnectionState()
    object Connected : WebSocketConnectionState
    data class Error(val message: String, val throwable: Throwable?) : WebSocketConnectionState()
}
```

### 5. Security APIs

#### EncryptedStorage
```kotlin
interface EncryptedStorage {
    fun isPaired(): Boolean
    fun getDeviceToken(): String?
    fun getGatewayUrl(): String?
    fun setGatewayUrl(url: String)
    fun getString(key: String): String?
    fun setString(key: String, value: String)
    fun clear()
}
```

### 6. UI State Models

#### MainUiState
```kotlin
data class MainUiState(
    val sessions: List<SessionUi> = emptyList(),
    val currentSession: SessionUi? = null,
    val currentGateway: GatewayConfigUi? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

#### SessionUiState
```kotlin
data class SessionUiState(
    val sessionId: String? = null,
    val chatMessages: List<MessageUi> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val attachments: List<AttachmentUi> = emptyList()
)
```

#### MessageUi
```kotlin
data class MessageUi(
    val id: String,
    val role: MessageRole,
    val timestamp: Long,
    val content: List<MessageContentItem>,
    val isStreaming: Boolean = false,
    val status: MessageStatus = MessageStatus.SUCCESS
)
```

---

## 使用示例

### 连接 Gateway
```kotlin
viewModel.connect("192.168.1.1:18789")
```

### 发送消息
```kotlin
sessionViewModel.sendMessage("Hello, world!")
```

### 格式化时间
```kotlin
val relativeTime = DateTimeUtils.formatRelativeTime(timestamp)
// 输出: "5 分钟前"
```

### 验证输入
```kotlin
if (ValidationUtils.isValidEmail(email)) {
    // 有效邮箱
}
```

---

**ClawChat Team**  
2026-04-01