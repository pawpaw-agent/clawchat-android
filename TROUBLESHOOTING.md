# ClawChat 故障排除指南

## 常见问题

### 连接问题

#### 问题：无法连接到 Gateway

**症状**:
- 显示"连接错误"
- 长时间显示"连接中..."

**解决方案**:

1. **检查网络连接**
   ```bash
   ping 192.168.1.1
   ```

2. **检查 Gateway 是否运行**
   ```bash
   curl http://192.168.1.1:18789/health
   ```

3. **检查防火墙设置**
   - 确保端口 18789 未被阻止
   - 检查 Gateway 防火墙规则

4. **检查 URL 格式**
   - 正确：`192.168.1.1:18789`
   - 错误：`http://192.168.1.1:18789`

#### 问题：WebSocket 连接失败

**错误日志**:
```
WebSocket connection failed: Connection refused
```

**解决方案**:
1. 确认 Gateway 支持 WebSocket
2. 检查是否使用正确的协议（ws:// 或 wss://）
3. 验证 token 是否有效

---

### 认证问题

#### 问题：配对失败

**症状**:
- 显示"配对失败"
- Token 无效错误

**解决方案**:

1. **检查 pairing code**
   ```
   Settings > Gateway > Pairing Code
   ```

2. **重新生成 pairing code**
   - 在 Gateway 端重新生成
   - 使用新的 code 重新配对

3. **清除旧配对**
   ```kotlin
   encryptedStorage.clear()
   ```

#### 问题：Token 过期

**症状**:
- 连接后立即断开
- 显示"认证失败"

**解决方案**:
1. 重新配对获取新 token
2. 检查设备时间是否正确
3. 清除应用数据重新配对

---

### 消息问题

#### 问题：消息发送失败

**错误**:
```
发送失败：网络错误
```

**解决方案**:

1. **检查连接状态**
   - 确保已连接到 Gateway
   - 查看连接状态指示器

2. **检查消息格式**
   - 消息不能为空
   - 附件大小 < 10MB

3. **重试发送**
   - 点击消息的重试按钮
   - 或使用"继续生成"功能

#### 问题：消息显示异常

**症状**:
- Markdown 渲染错误
- 图片无法加载

**解决方案**:

1. **清除缓存**
   ```
   Settings > Storage > Clear Cache
   ```

2. **检查图片格式**
   - 支持：JPEG, PNG, WebP, GIF
   - 不支持：BMP, TIFF

---

### 性能问题

#### 问题：应用启动慢

**可能原因**:
- 数据库过大
- 缓存过多
- 设备性能低

**解决方案**:

1. **清理旧数据**
   ```
   Settings > Storage > Clean Old Sessions
   ```

2. **减少消息缓存**
   - 自动保留最近 500 条消息
   - 手动删除旧会话

#### 问题：滚动卡顿

**解决方案**:

1. **减少动画**
   - 关闭不必要的动画效果

2. **清理消息列表**
   - 删除不必要的历史消息

---

### 崩溃问题

#### 问题：应用崩溃

**收集日志**:
```bash
adb logcat -s ClawChat:* AndroidRuntime:E
```

**常见崩溃原因**:

1. **内存不足**
   - 关闭其他应用
   - 清理会话历史

2. **数据损坏**
   - 清除应用数据
   - 重新配对

3. **版本不兼容**
   - 更新到最新版本
   - 检查 Android 版本（需要 8.0+）

---

### 数据问题

#### 问题：会话丢失

**预防措施**:
- 定期导出会话
- 启用自动备份

**恢复步骤**:
1. 检查归档会话
2. 从导出文件恢复
3. 从 Gateway 重新同步

#### 问题：设置丢失

**原因**:
- 应用数据清除
- 应用重装

**解决方案**:
- 重新配置设置
- 从备份恢复

---

## 调试技巧

### 启用调试模式

```
Settings > Developer Options > Enable Debug Mode
```

### 查看日志

```bash
# 应用日志
adb logcat -s AppLog:*

# 网络日志
adb logcat -s Network:*

# 错误日志
adb logcat -s Error:*
```

### 检查数据库

```bash
adb shell
run-as com.openclaw.clawchat
sqlite3 databases/clawchat.db
```

---

## 获取帮助

### 收集诊断信息

1. 打开 Settings > About
2. 点击 "Send Feedback"
3. 包含以下信息：
   - 设备型号
   - Android 版本
   - 应用版本
   - 问题描述
   - 日志文件

### 联系支持

- GitHub Issues: https://github.com/openclaw/clawchat/issues
- Email: support@openclaw.com
- Discord: https://discord.gg/clawd

---

**ClawChat Team**  
2026-04-01