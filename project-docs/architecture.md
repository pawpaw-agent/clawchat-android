# ClawChat Android 技术架构文档

> 模块边界 · 数据流 · 接口规范 · 数据库设计 · 网络层

**版本**: 1.1.0  
**最后更新**: 2026-03-18  
**状态**: 架构定义

---

## 变更日志

### v1.1.0 (2026-03-18) - Domain 层创建

- ✅ 创建完整的 Domain 层架构
- ✅ 添加领域模型：`User`, `Session`, `Message`, `Attachment`, `GatewayConfig`, `DeviceToken`, `ConnectionStatus`
- ✅ 添加 Repository 接口：`SessionRepository`, `ConnectionRepository`, `SettingsRepository`
- ✅ 添加 UseCase：`SendMessage`, `CreateSession`, `DeleteSession`, `GetSessionHistory`, `PairDevice`, `ConnectGateway`, `ReceiveMessage`
- ✅ 添加单元测试框架（领域模型 + UseCase）
- ✅ 纯 Kotlin 实现，无 Android 依赖

---

---

## 1. 模块边界定义

### 1.1 架构分层

采用 **Clean Architecture + MVVM** 混合架构，分为四层：

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │   Screens       │  │   Components    │  │  ViewModels │  │
│  │   (Composables) │  │   (UI Widgets)  │  │   (State)   │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    Use Cases                             ││
│  │  (ConnectGateway, SendMessage, PairDevice, etc.)        ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    Domain Models                         ││
│  │  (Session, Message, DeviceInfo, ConnectionStatus)       ││
│  └─────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │  Repositories   │  │   Data Sources  │  │    DTOs     │  │
│  │  (Interfaces)   │  │   (Local/Remote)│  │  (Mappers)  │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Network    │  │   Storage   │  │      Security       │  │
│  │  (OkHttp)   │  │  (Room/SP)  │  │   (Keystore/Crypto) │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 模块职责

| 模块 | 包路径 | 职责 | 依赖方向 |
|------|--------|------|----------|
| **UI** | `ui.screens`, `ui.components` | 渲染界面，用户交互 | → ViewModel |
| **ViewModel** | `viewmodel` | 状态管理，UI 逻辑 | → UseCase |
| **UseCase** | `domain.usecase` | 业务逻辑，单一职责 | → Repository |
| **Repository** | `data.repository` | 数据聚合，缓存策略 | → DataSource |
| **DataSource** | `data.local`, `data.remote` | 数据访问抽象 | → Infrastructure |
| **Infrastructure** | `di`, `util`, `security` | 基础服务，工具类 | 无依赖 |

### 1.3 模块依赖规则

```
UI → ViewModel → UseCase → Repository → DataSource → Infrastructure
     ←───────────────────────────────────────────────── (Flow/State)
```

**禁止**:
- 上层直接依赖下层实现类
- 跨层调用（如 UI 直接调用 API）
- 循环依赖

---

## 2. 数据流设计

### 2.1 整体数据流

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│     User     │     │    UI Layer  │     │  ViewModel   │
│  (Interaction)│────>│  (Compose)   │────>│   (State)    │
└──────────────┘     └──────────────┘     └──────────────┘
                                                │
                                                ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  DataSource  │<────│  Repository  │<────│   UseCase    │
│ (Local/Remote)│     │   (Cache)    │     │  (Business)  │
└──────────────┘     └──────────────┘     └──────────────┘
```

### 2.2 WebSocket 消息流

```
┌─────────────────────────────────────────────────────────────────┐
│                    WebSocket Message Flow                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Gateway ────> WebSocketService ────> MessageRepository         │
│     │                │                      │                   │
│     │                │                      ▼                   │
│     │                │              SessionViewModel            │
│     │                │                      │                   │
│     │                │                      ▼                   │
│     │                │              UI (Compose)                │
│     │                │                      │                   │
│     ▼                ▼                      ▼                   │
│  (systemEvent)   (parse)              (StateFlow)            (render)
│                                                                 │
│  User Input ────> ViewModel ────> Repository ────> WebSocket    │
│     │                │                │                Service   │
│     │                │                │                  │       │
│     ▼                ▼                ▼                  ▼       │
│  (text)         (validate)      (enqueue)          (send)        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.3 状态管理

使用 Kotlin `StateFlow` 进行单向数据流：

```kotlin
// ViewModel 状态定义
data class UiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val sessions: List<Session> = emptyList(),
    val currentSession: Session? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

// ViewModel 实现
@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectGateway: ConnectGateway,
    private val observeSessions: ObserveSessions,
    private val sendMessage: SendMessage
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            observeSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }
    
    fun connect(gatewayUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            connectGateway(gatewayUrl)
                .onSuccess { _uiState.update { it.copy(connectionStatus = Connected, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        }
    }
}
```

### 2.4 事件处理

```kotlin
// 单向事件通道
sealed class UiEvent {
    data class NavigateToSession(val sessionId: String) : UiEvent()
    data object ShowPairingDialog : UiEvent()
    data class ShowError(val message: String) : UiEvent()
    data object ConnectionLost : UiEvent()
}

// ViewModel 中的事件通道
private val _events = Channel<UiEvent>(Channel.BUFFERED)
val events: Flow<UiEvent> = _events.receiveAsFlow()

// 使用
fun onSessionClick(sessionId: String) {
    viewModelScope.launch {
        _events.send(UiEvent.NavigateToSession(sessionId))
    }
}
```

---

## 3. 核心接口规范

### 3.1 Repository 接口

```kotlin
// SessionRepository.kt
interface SessionRepository {
    /** 获取所有会话列表 */
    fun observeSessions(): Flow<List<Session>>
    
    /** 获取单个会话详情 */
    suspend fun getSession(sessionId: String): Session?
    
    /** 获取会话消息历史 */
    fun observeMessages(sessionId: String): Flow<List<Message>>
    
    /** 创建新会话 */
    suspend fun createSession(model: String, thinking: Boolean = false): Result<Session>
    
    /** 发送消息 */
    suspend fun sendMessage(sessionId: String, content: String, attachments: List<Attachment> = emptyList()): Result<Message>
    
    /** 终止会话 */
    suspend fun terminateSession(sessionId: String): Result<Unit>
    
    /** 删除会话 */
    suspend fun deleteSession(sessionId: String): Result<Unit>
}

// ConnectionRepository.kt
interface ConnectionRepository {
    /** 观察连接状态 */
    fun observeConnectionStatus(): Flow<ConnectionStatus>
    
    /** 连接到 Gateway */
    suspend fun connect(config: GatewayConfig): Result<Unit>
    
    /** 断开连接 */
    suspend fun disconnect(): Result<Unit>
    
    /** 获取当前延迟 */
    suspend fun getLatency(): Long?
    
    /** 执行设备配对 */
    suspend fun pairDevice(config: GatewayConfig): Result<DeviceToken>
}

// SettingsRepository.kt
interface SettingsRepository {
    /** 获取所有 Gateway 配置 */
    fun observeGatewayConfigs(): Flow<List<GatewayConfig>>
    
    /** 保存 Gateway 配置 */
    suspend fun saveGatewayConfig(config: GatewayConfig): Result<Unit>
    
    /** 删除 Gateway 配置 */
    suspend fun deleteGatewayConfig(id: String): Result<Unit>
    
    /** 获取当前选中的 Gateway */
    suspend fun getCurrentGateway(): GatewayConfig?
    
    /** 设置当前 Gateway */
    suspend fun setCurrentGateway(id: String): Result<Unit>
    
    /** 获取用户偏好 */
    suspend fun getPreferences(): UserPreferences
    
    /** 保存用户偏好 */
    suspend fun savePreferences(preferences: UserPreferences): Result<Unit>
}
```

### 3.2 DataSource 接口

```kotlin
// RemoteDataSource.kt
interface GatewayRemoteDataSource {
    /** 建立 WebSocket 连接 */
    suspend fun connect(url: String, token: String?): Result<WebSocketConnection>
    
    /** 发送消息 */
    suspend fun send(connection: WebSocketConnection, message: GatewayMessage): Result<Unit>
    
    /** 关闭连接 */
    suspend fun close(connection: WebSocketConnection): Result<Unit>
    
    /** 执行配对请求 */
    suspend fun requestPairing(url: String, publicKey: String, deviceId: String): Result<PairingResponse>
    
    /** 轮询配对状态 */
    suspend fun pollPairingStatus(url: String, requestId: String): Result<PairingStatus>
}

// LocalDataSource.kt
interface SettingsLocalDataSource {
    /** 获取保存的 Gateway 配置 */
    suspend fun getGatewayConfigs(): List<GatewayConfigEntity>
    
    /** 保存 Gateway 配置 */
    suspend fun saveGatewayConfig(config: GatewayConfigEntity): Unit
    
    /** 删除 Gateway 配置 */
    suspend fun deleteGatewayConfig(id: String): Unit
    
    /** 获取设备令牌 */
    suspend fun getDeviceToken(): String?
    
    /** 保存设备令牌 */
    suspend fun saveDeviceToken(token: String): Unit
    
    /** 清除设备令牌 */
    suspend fun clearDeviceToken(): Unit
}

interface MessageLocalDataSource {
    /** 缓存消息 */
    suspend fun cacheMessages(messages: List<MessageEntity>): Unit
    
    /** 获取缓存消息 */
    suspend fun getMessages(sessionId: String): List<MessageEntity>
    
    /** 清除会话缓存 */
    suspend fun clearSessionCache(sessionId: String): Unit
}
```

### 3.3 UseCase 接口

```kotlin
// ConnectGateway.kt
class ConnectGateway @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(gatewayUrl: String? = null): Result<Unit> {
        val config = gatewayUrl?.let { GatewayConfig(it) } 
            ?: settingsRepository.getCurrentGateway()
            ?: return Failure("No gateway configured")
        
        return connectionRepository.connect(config)
    }
}

// SendMessage.kt
class SendMessage @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        content: String,
        attachments: List<Attachment> = emptyList()
    ): Result<Message> {
        if (content.isBlank() && attachments.isEmpty()) {
            return Failure("Message content cannot be empty")
        }
        
        return sessionRepository.sendMessage(sessionId, content, attachments)
    }
}

// PairDevice.kt
class PairDevice @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val securityManager: SecurityManager
) {
    suspend operator fun invoke(config: GatewayConfig): Result<DeviceToken> {
        // 1. 生成密钥对（如不存在）
        val keyPair = securityManager.getOrCreateKeyPair()
        
        // 2. 生成设备指纹
        val deviceId = securityManager.generateDeviceId()
        
        // 3. 发起配对请求
        return connectionRepository.pairDevice(config.copy(
            publicKey = keyPair.publicKey,
            deviceId = deviceId
        ))
    }
}
```

### 3.4 WebSocket 服务接口

```kotlin
// WebSocketService.kt
interface WebSocketService {
    /** 连接状态流 */
    val connectionState: StateFlow<WebSocketConnectionState>
    
    /** 消息接收流 */
    val incomingMessages: Flow<GatewayMessage>
    
    /** 建立连接 */
    suspend fun connect(url: String, token: String?): Result<Unit>
    
    /** 发送消息 */
    suspend fun send(message: GatewayMessage): Result<Unit>
    
    /** 断开连接 */
    suspend fun disconnect(): Result<Unit>
    
    /** 获取连接延迟 */
    suspend fun measureLatency(): Long?
}

// 连接状态
sealed class WebSocketConnectionState {
    data object Disconnected : WebSocketConnectionState()
    data object Connecting : WebSocketConnectionState()
    data object Connected : WebSocketConnectionState()
    data class Disconnecting(val reason: String) : WebSocketConnectionState()
    data class Error(val throwable: Throwable) : WebSocketConnectionState()
}
```

### 3.5 安全模块接口

```kotlin
// SecurityManager.kt
interface SecurityManager {
    /** 获取或创建设备密钥对 */
    fun getOrCreateKeyPair(): KeyPair
    
    /** 生成设备唯一标识 */
    fun generateDeviceId(): String
    
    /** 签名挑战 */
    fun signChallenge(challenge: ByteArray): ByteArray
    
    /** 签名配对载荷 */
    fun signPairingPayload(payload: String): String
    
    /** 验证服务器签名（如需要） */
    fun verifyServerSignature(data: ByteArray, signature: ByteArray): Boolean
}
```

---

## 4. 数据库 Schema

### 4.1 实体设计

使用 Room 进行本地数据持久化：

```kotlin
// GatewayConfigEntity.kt
@Entity(tableName = "gateway_configs")
data class GatewayConfigEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 18789,
    val useTls: Boolean = false,
    val tlsFingerprint: String? = null,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// SessionEntity.kt
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val label: String?,
    val model: String?,
    val status: String, // "running", "paused", "terminated"
    val createdAt: Long,
    val lastActivityAt: Long,
    val messageCount: Int = 0
)

// MessageEntity.kt
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: String? = null, // JSON array of attachment metadata
    val metadata: String? = null // JSON object for extra data
)

// DeviceInfoEntity.kt
@Entity(tableName = "device_info")
data class DeviceInfoEntity(
    @PrimaryKey val id: String = "device_info",
    val deviceId: String,
    val publicKey: String,
    val deviceToken: String?,
    val pairedAt: Long?,
    val lastConnectedAt: Long?
)
```

### 4.2 DAO 定义

```kotlin
// GatewayConfigDao.kt
@Dao
interface GatewayConfigDao {
    @Query("SELECT * FROM gateway_configs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<GatewayConfigEntity>>
    
    @Query("SELECT * FROM gateway_configs WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): GatewayConfigEntity?
    
    @Query("SELECT * FROM gateway_configs WHERE id = :id")
    suspend fun getById(id: String): GatewayConfigEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: GatewayConfigEntity)
    
    @Delete
    suspend fun delete(config: GatewayConfigEntity)
    
    @Query("UPDATE gateway_configs SET isCurrent = 0")
    suspend fun clearCurrent()
    
    @Query("UPDATE gateway_configs SET isCurrent = 1 WHERE id = :id")
    suspend fun setCurrent(id: String)
}

// SessionDao.kt
@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY lastActivityAt DESC")
    fun getAll(): Flow<List<SessionEntity>>
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)
    
    @Update
    suspend fun update(session: SessionEntity)
    
    @Delete
    suspend fun delete(session: SessionEntity)
    
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

// MessageDao.kt
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int
}

// DeviceInfoDao.kt
@Dao
interface DeviceInfoDao {
    @Query("SELECT * FROM device_info WHERE id = 'device_info'")
    suspend fun get(): DeviceInfoEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(info: DeviceInfoEntity)
    
    @Update
    suspend fun update(info: DeviceInfoEntity)
}
```

### 4.3 Database 定义

```kotlin
// ClawChatDatabase.kt
@Database(
    entities = [
        GatewayConfigEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        DeviceInfoEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ClawChatDatabase : RoomDatabase() {
    abstract fun gatewayConfigDao(): GatewayConfigDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun deviceInfoDao(): DeviceInfoDao
    
    companion object {
        private const val DATABASE_NAME = "clawchat_db"
        
        fun buildDatabase(context: Context): ClawChatDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ClawChatDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration() // 开发阶段
            .build()
        }
    }
}

// TypeConverters.kt
class Converters {
    @TypeConverter
    fun fromJsonString(value: String?): List<Attachment>? {
        return value?.let { Json.decodeFromString(it) }
    }
    
    @TypeConverter
    fun toJsonString(attachments: List<Attachment>?): String? {
        return attachments?.let { Json.encodeToString(it) }
    }
}
```

### 4.4 数据模型转换

```kotlin
// Entity <-> Domain 映射
fun GatewayConfigEntity.toDomain(): GatewayConfig = GatewayConfig(
    id = id,
    name = name,
    host = host,
    port = port,
    useTls = useTls,
    tlsFingerprint = tlsFingerprint
)

fun GatewayConfig.toEntity(): GatewayConfigEntity = GatewayConfigEntity(
    id = id ?: UUID.randomUUID().toString(),
    name = name,
    host = host,
    port = port,
    useTls = useTls,
    tlsFingerprint = tlsFingerprint
)

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    label = label,
    model = model,
    status = SessionStatus.fromString(status),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastActivityAt = Instant.fromEpochMilliseconds(lastActivityAt),
    messageCount = messageCount
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    sessionId = sessionId,
    role = MessageRole.fromString(role),
    content = content,
    timestamp = Instant.fromEpochMilliseconds(timestamp),
    attachments = attachments?.let { Json.decodeFromString(it) } ?: emptyList()
)
```

---

## 5. 网络层设计

### 5.1 网络架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Network Layer Architecture                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                  OkHttp Client                            │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              LoggingInterceptor                      │  │  │
│  │  │  (DEBUG only, redacts sensitive data)               │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              SignatureInterceptor                    │  │  │
│  │  │  (Adds X-ClawChat-Signature header)                 │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              CertificatePinner                       │  │  │
│  │  │  (TLS Pinning for production)                       │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              WebSocketService (OkHttp WebSocket)          │  │
│  │  - Connection management                                  │  │
│  │  - Message serialization/deserialization                  │  │
│  │  - Reconnection logic                                     │  │
│  │  - Latency monitoring                                     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 OkHttp 客户端配置

```kotlin
// NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        securityManager: SecurityManager,
        @ApplicationContext context: Context
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            SecureLogger.d(message.redactSensitive())
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY 
                    else HttpLoggingInterceptor.Level.NONE
        }
        
        val signatureInterceptor = SignatureInterceptor(securityManager)
        
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(signatureInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // WebSocket 心跳
        
        // 生产环境添加证书固定
        if (!BuildConfig.DEBUG) {
            val certificatePinner = CertificatePinner.Builder()
                .add("*.openclaw.ai", "sha256/production_pin_1")
                .add("*.openclaw.ai", "sha256/production_pin_2")
                .build()
            builder.certificatePinner(certificatePinner)
        }
        
        return builder.build()
    }
    
    @Provides
    @Singleton
    fun provideWebSocketService(
        okHttpClient: OkHttpClient,
        securityManager: SecurityManager
    ): WebSocketService = OkHttpWebSocketService(okHttpClient, securityManager)
}

// SignatureInterceptor.kt
class SignatureInterceptor(
    private val securityManager: SecurityManager
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val timestamp = System.currentTimeMillis()
        val nonce = generateNonce()
        
        // 构建签名字符串
        val dataToSign = "${request.url.path}\n$timestamp\n$nonce"
        val signature = securityManager.signChallenge(dataToSign.toByteArray())
            .toBase64()
        
        val signedRequest = request.newBuilder()
            .addHeader("X-ClawChat-Timestamp", timestamp.toString())
            .addHeader("X-ClawChat-Nonce", nonce)
            .addHeader("X-ClawChat-Signature", signature)
            .build()
        
        return chain.proceed(signedRequest)
    }
    
    private fun generateNonce(): String = UUID.randomUUID().toString()
}
```

### 5.3 WebSocket 服务实现

```kotlin
// OkHttpWebSocketService.kt
@Singleton
class OkHttpWebSocketService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val securityManager: SecurityManager
) : WebSocketService {
    
    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()
    
    private val _incomingMessages = MutableSharedFlow<GatewayMessage>(replay = 0)
    override val incomingMessages: Flow<GatewayMessage> = _incomingMessages.asSharedFlow()
    
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var latencyMeasurements = mutableListOf<Long>()
    
    override suspend fun connect(url: String, token: String?): Result<Unit> {
        if (_connectionState.value is Connected) {
            return Result.success(Unit)
        }
        
        _connectionState.value = Connecting
        
        return try {
            val request = buildWebSocketRequest(url, token)
            
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionState.value = Connected
                    startLatencyMonitoring()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    processIncomingMessage(text)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    _connectionState.value = Error(t)
                    scheduleReconnect(url, token)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = Disconnecting(reason)
                }
            })
            
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = Error(e)
            Result.failure(e)
        }
    }
    
    private fun buildWebSocketRequest(url: String, token: String?): Request {
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString()
        val dataToSign = "/ws\n$timestamp\n$nonce"
        val signature = securityManager.signChallenge(dataToSign.toByteArray()).toBase64()
        
        return Request.Builder()
            .url(url)
            .addHeader("X-ClawChat-Timestamp", timestamp.toString())
            .addHeader("X-ClawChat-Nonce", nonce)
            .addHeader("X-ClawChat-Signature", signature)
            .token?.let { addHeader("Authorization", "Bearer $it") }
            ?.build() ?: build()
    }
    
    private fun processIncomingMessage(text: String) {
        try {
            val message = Json.decodeFromString<GatewayMessage>(text)
            viewModelScope.launch {
                _incomingMessages.emit(message)
            }
        } catch (e: Exception) {
            SecureLogger.e("Failed to parse message: ${e.message}")
        }
    }
    
    override suspend fun send(message: GatewayMessage): Result<Unit> {
        val ws = webSocket ?: return Result.failure(IllegalStateException("Not connected"))
        
        return try {
            val json = Json.encodeToString(message)
            ws.send(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        reconnectJob?.cancel()
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        _connectionState.value = Disconnected
        return Result.success(Unit)
    }
    
    private fun scheduleReconnect(url: String, token: String?, delayMs: Long = 5000) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            connect(url, token)
        }
    }
    
    private fun startLatencyMonitoring() {
        viewModelScope.launch {
            while (_connectionState.value is Connected) {
                val latency = measureLatency()
                latency?.let {
                    latencyMeasurements.add(it)
                    if (latencyMeasurements.size > 10) {
                        latencyMeasurements.removeAt(0)
                    }
                }
                delay(60000) // 每分钟测量一次
            }
        }
    }
    
    override suspend fun measureLatency(): Long? {
        val ws = webSocket ?: return null
        
        val start = System.currentTimeMillis()
        val pingMessage = GatewayMessage.Ping(System.currentTimeMillis())
        
        return try {
            ws.send(Json.encodeToString(pingMessage))
            // 等待 Pong 响应（简化实现，实际需匹配请求 ID）
            delay(1000)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            null
        }
    }
}
```

### 5.4 网络消息格式

```kotlin
// GatewayMessage.kt
@Serializable
sealed class GatewayMessage {
    @SerialName("type")
    abstract val type: String
    
    @Serializable
    @SerialName("systemEvent")
    data class SystemEvent(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "systemEvent"
    }
    
    @Serializable
    @SerialName("userMessage")
    data class UserMessage(
        val sessionId: String,
        val content: String,
        val attachments: List<AttachmentDto> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "userMessage"
    }
    
    @Serializable
    @SerialName("assistantMessage")
    data class AssistantMessage(
        val sessionId: String,
        val content: String,
        val model: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayMessage() {
        override val type = "assistantMessage"
    }
    
    @Serializable
    @SerialName("ping")
    data class Ping(
        val timestamp: Long
    ) : GatewayMessage() {
        override val type = "ping"
    }
    
    @Serializable
    @SerialName("pong")
    data class Pong(
        val timestamp: Long,
        val latency: Long
    ) : GatewayMessage() {
        override val type = "pong"
    }
}

// AttachmentDto.kt
@Serializable
data class AttachmentDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val url: String? = null,
    val base64: String? = null // 小文件可内联
)
```

### 5.5 错误处理与重试

```kotlin
// NetworkErrorHandler.kt
sealed class NetworkError {
    data object Timeout : NetworkError()
    data object NoConnection : NetworkError()
    data object Unauthorized : NetworkError()
    data object ServerError : NetworkError()
    data class Unknown(val throwable: Throwable) : NetworkError()
}

fun Throwable.toNetworkError(): NetworkError {
    return when (this) {
        is SocketTimeoutException -> NetworkError.Timeout
        is UnknownHostException -> NetworkError.NoConnection
        is IOException -> NetworkError.NoConnection
        else -> {
            // 检查 HTTP 响应码
            if (this is HttpException) {
                when (this.code()) {
                    401, 403 -> NetworkError.Unauthorized
                    in 500..599 -> NetworkError.ServerError
                    else -> NetworkError.Unknown(this)
                }
            } else {
                NetworkError.Unknown(this)
            }
        }
    }
}

// 重试策略
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay
    var lastResult: Result<T>? = null
    
    repeat(maxRetries) { attempt ->
        val result = block()
        if (result.isSuccess) return result
        
        lastResult = result
        if (attempt < maxRetries - 1) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    
    return lastResult ?: Result.failure(Exception("Unknown error"))
}
```

### 5.6 Tailscale 连接支持

```kotlin
// TailscaleManager.kt
class TailscaleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 检查 Tailscale 是否已连接 */
    fun isTailscaleConnected(): Boolean {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name == "tun0" || iface.name == "tailscale0") {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    
    /** 获取 Tailscale IP 地址 */
    fun getTailscaleIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name == "tun0" || iface.name == "tailscale0") {
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            return address.hostAddress
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /** 解析 MagicDNS 名称 */
    suspend fun resolveMagicDns(name: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(name).hostAddress
            } catch (e: Exception) {
                null
            }
        }
    }
}
```

---

## 附录

### A. 依赖注入图

```
@Singleton Component
├── OkHttpClient
├── WebSocketService
├── SecurityManager
├── ClawChatDatabase
│   ├── GatewayConfigDao
│   ├── SessionDao
│   ├── MessageDao
│   └── DeviceInfoDao
├── Repositories
│   ├── ConnectionRepository
│   ├── SessionRepository
│   └── SettingsRepository
└── UseCases
    ├── ConnectGateway
    ├── SendMessage
    ├── PairDevice
    └── ...
```

### B. 线程模型

| 操作 | 调度器 | 说明 |
|------|--------|------|
| UI 状态更新 | Dispatchers.Main | Compose 渲染 |
| 网络请求 | Dispatchers.IO | OkHttp 自带线程池 |
| 数据库操作 | Dispatchers.IO | Room 自动切换 |
| 加密运算 | Dispatchers.Default | CPU 密集型 |
| 消息解析 | Dispatchers.Default | JSON 序列化 |

### C. 性能优化

1. **连接复用**: 单例 OkHttpClient，连接池复用
2. **消息缓存**: Room 缓存最近 100 条消息/会话
3. **图片加载**: Coil 自动缓存，内存 + 磁盘双层
4. **状态共享**: StateFlow 状态共享，避免重复请求
5. **懒加载**: Hilt 依赖注入，按需创建

---

*文档结束*
