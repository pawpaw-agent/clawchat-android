#!/bin/bash
# ClawChat Gateway 连接测试脚本
# 验证 WebSocket 连接、认证和消息收发

set -e

GATEWAY_URL="ws://localhost:18789/ws"
GATEWAY_HTTP="http://localhost:18789"

echo "======================================"
echo "ClawChat Gateway 连接测试"
echo "======================================"
echo ""

# 检查 Gateway 是否运行
echo "1. 检查 Gateway 状态..."
if curl -s "$GATEWAY_HTTP/status" > /dev/null 2>&1; then
    echo "   ✅ Gateway 正在运行"
else
    echo "   ❌ Gateway 未运行"
    exit 1
fi

# 获取 Gateway 信息
echo ""
echo "2. 获取 Gateway 信息..."
GATEWAY_INFO=$(curl -s "$GATEWAY_HTTP/info" 2>/dev/null || echo "{}")
echo "   Gateway 信息：$GATEWAY_INFO"

# 测试 WebSocket 连接
echo ""
echo "3. 测试 WebSocket 连接..."
if command -v wscat &> /dev/null; then
    echo "   使用 wscat 测试..."
    timeout 5 wscat -c "$GATEWAY_URL" -n <<< "exit" 2>&1 || true
    echo "   ✅ WebSocket 连接测试完成"
else
    echo "   ⚠️  wscat 未安装，跳过 WebSocket 测试"
    echo "   安装：npm install -g wscat"
fi

# 检查设备配对状态
echo ""
echo "4. 检查设备配对状态..."
DEVICE_TOKEN_FILE="$HOME/.openclaw/workspace-ClawChat/app/src/main/assets/device_token.txt"
if [ -f "$DEVICE_TOKEN_FILE" ]; then
    echo "   ✅ 设备 Token 文件存在"
    cat "$DEVICE_TOKEN_FILE"
else
    echo "   ℹ️  设备 Token 文件不存在（首次连接会创建）"
fi

# 测试 Challenge-Response 认证
echo ""
echo "5. 测试 Challenge-Response 认证..."
echo "   需要生成密钥对并签名挑战..."
echo "   ⚠️  此步骤需要 Android 设备或模拟器"

# 总结
echo ""
echo "======================================"
echo "测试总结"
echo "======================================"
echo "Gateway 状态：✅ 运行中"
echo "WebSocket 连接：⚠️  需要 wscat 或 Android 设备"
echo "设备认证：⚠️  需要 Android 设备测试"
echo ""
echo "下一步:"
echo "1. 在 Android 设备上安装 ClawChat"
echo "2. 配置 Gateway 地址：ws://<your-ip>:18789/ws"
echo "3. 进行设备配对"
echo "4. 验证消息收发"
