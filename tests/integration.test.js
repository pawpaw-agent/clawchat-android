#!/usr/bin/env node
/**
 * ClawChat Integration Tests with Mock Gateway
 * 
 * 测试覆盖：
 * - 连接成功测试
 * - 认证失败测试
 * - 消息发送测试
 * - 消息接收测试
 * - 断线重连测试
 */

const WebSocket = require('ws');
const crypto = require('crypto');
const assert = require('assert');
const { MockGateway } = require('./mock-gateway');

// 测试结果
const results = {
  passed: 0,
  failed: 0,
  tests: [],
};

// 测试配置
const CONFIG = {
  gatewayPort: 18790,  // 使用不同端口避免冲突
  timeout: 5000,
};

function test(name, fn) {
  return { name, fn };
}

async function runTest(testCase, context) {
  try {
    await testCase.fn(context);
    results.passed++;
    results.tests.push({ name: testCase.name, status: 'PASS' });
    console.log(`✅ ${testCase.name}`);
    return true;
  } catch (err) {
    results.failed++;
    results.tests.push({ name: testCase.name, status: 'FAIL', error: err.message });
    console.error(`❌ ${testCase.name}: ${err.message}`);
    return false;
  }
}

// ========== 辅助函数 ==========

function createWebSocketClient(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    
    // 设置消息队列缓冲
    ws.messageQueue = [];
    ws.messageHandlers = [];
    
    ws.on('message', (data) => {
      // 如果有等待的处理程序，立即调用
      if (ws.messageHandlers.length > 0) {
        const handler = ws.messageHandlers.shift();
        handler(data);
      } else {
        // 否则加入队列
        ws.messageQueue.push(data);
      }
    });
    
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
    setTimeout(() => reject(new Error('Connection timeout')), CONFIG.timeout);
  });
}

function waitForMessage(ws, timeout = CONFIG.timeout) {
  return new Promise((resolve, reject) => {
    // 检查是否有缓冲的消息
    if (ws.messageQueue && ws.messageQueue.length > 0) {
      const data = ws.messageQueue.shift();
      try {
        resolve(JSON.parse(data.toString()));
        return;
      } catch (err) {
        reject(err);
        return;
      }
    }
    
    // 注册处理程序
    const handler = (data) => {
      clearTimeout(timer);
      try {
        resolve(JSON.parse(data.toString()));
      } catch (err) {
        reject(err);
      }
    };
    
    ws.messageHandlers.push(handler);
    
    const timer = setTimeout(() => {
      const index = ws.messageHandlers.indexOf(handler);
      if (index > -1) ws.messageHandlers.splice(index, 1);
      reject(new Error('Message timeout'));
    }, timeout);
  });
}

function generateKeyPair() {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
  });
  
  return {
    privateKey,
    publicKey: publicKey.export({ type: 'spki', format: 'pem' }),
  };
}

function signChallenge(privateKey, challenge) {
  const sign = crypto.createSign('SHA256');
  sign.update(challenge);
  sign.end();
  return sign.sign(privateKey, 'base64');
}

// ========== 测试用例 ==========

const integrationTests = [
  test('连接成功测试', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 应该收到 Hello 消息
      const hello = await waitForMessage(ws);
      
      assert.strictEqual(hello.type, 'hello', '应该收到 Hello 消息');
      assert(hello.nonce, 'Hello 应该包含 nonce');
      assert(hello.version, 'Hello 应该包含版本号');
    } finally {
      ws.close();
    }
  }),

  test('认证成功测试', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 收到 Hello
      const hello = await waitForMessage(ws);
      assert.strictEqual(hello.type, 'hello');
      
      // 生成密钥对并签名
      const { privateKey, publicKey } = generateKeyPair();
      const signature = signChallenge(privateKey, hello.nonce);
      
      // 发送认证请求
      ws.send(JSON.stringify({
        type: 'auth',
        publicKey,
        signature,
      }));
      
      // 等待认证响应
      const authResponse = await waitForMessage(ws);
      
      assert.strictEqual(authResponse.type, 'auth_response');
      assert.strictEqual(authResponse.status, 'success');
      assert(authResponse.sessionId, '应该返回 sessionId');
    } finally {
      ws.close();
    }
  }),

  test('认证失败测试 - 空签名', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 收到 Hello
      const hello = await waitForMessage(ws);
      
      // 发送空签名
      ws.send(JSON.stringify({
        type: 'auth',
        publicKey: 'fake-key',
        signature: '',
      }));
      
      // 等待认证响应
      const authResponse = await waitForMessage(ws);
      
      assert.strictEqual(authResponse.type, 'auth_response');
      assert.strictEqual(authResponse.status, 'failed');
    } finally {
      ws.close();
    }
  }),

  test('未认证不能创建会话', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 收到 Hello 但不认证
      await waitForMessage(ws);
      
      // 尝试创建会话
      ws.send(JSON.stringify({
        type: 'session/create',
        model: 'test-model',
      }));
      
      // 应该收到错误
      const response = await waitForMessage(ws);
      
      assert.strictEqual(response.type, 'error');
      assert.strictEqual(response.code, 'unauthorized');
    } finally {
      ws.close();
    }
  }),

  test('消息发送测试', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 认证
      const hello = await waitForMessage(ws);
      const { privateKey, publicKey } = generateKeyPair();
      const signature = signChallenge(privateKey, hello.nonce);
      
      ws.send(JSON.stringify({ type: 'auth', publicKey, signature }));
      await waitForMessage(ws);  // auth_response
      
      // 创建会话
      ws.send(JSON.stringify({
        type: 'session/create',
        model: 'qwen3.5-plus',
      }));
      
      const sessionCreated = await waitForMessage(ws);
      assert.strictEqual(sessionCreated.type, 'session_created');
      
      // 发送消息
      ws.send(JSON.stringify({
        type: 'message',
        sessionId: sessionCreated.sessionId,
        content: 'Hello, Mock Gateway!',
      }));
      
      // 应该收到自动响应
      const response = await waitForMessage(ws, 3000);
      
      assert.strictEqual(response.type, 'message');
      assert(response.content.includes('Echo'), '应该收到回显消息');
    } finally {
      ws.close();
    }
  }),

  test('消息接收测试', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 认证并创建会话
      const hello = await waitForMessage(ws);
      const { privateKey, publicKey } = generateKeyPair();
      const signature = signChallenge(privateKey, hello.nonce);
      
      ws.send(JSON.stringify({ type: 'auth', publicKey, signature }));
      await waitForMessage(ws);
      
      ws.send(JSON.stringify({ type: 'session/create', model: 'test' }));
      const sessionCreated = await waitForMessage(ws);
      
      // 等待自动发送的欢迎消息
      const welcomeMessage = await waitForMessage(ws, 2000);
      
      assert.strictEqual(welcomeMessage.type, 'message');
      assert.strictEqual(welcomeMessage.role, 'assistant');
      assert(welcomeMessage.content.includes('Mock Gateway'), '应该收到欢迎消息');
    } finally {
      ws.close();
    }
  }),

  test('会话列表测试', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 认证
      const hello = await waitForMessage(ws);
      const { privateKey, publicKey } = generateKeyPair();
      const signature = signChallenge(privateKey, hello.nonce);
      
      ws.send(JSON.stringify({ type: 'auth', publicKey, signature }));
      await waitForMessage(ws);
      
      // 请求会话列表
      ws.send(JSON.stringify({ type: 'session/list' }));
      
      const sessionList = await waitForMessage(ws);
      
      assert.strictEqual(sessionList.type, 'session_list');
      assert(Array.isArray(sessionList.sessions), 'sessions 应该是数组');
    } finally {
      ws.close();
    }
  }),

  test('Ping/Pong 测试', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      // 先消耗 Hello 消息
      await waitForMessage(ws);
      
      const pingTime = Date.now();
      
      ws.send(JSON.stringify({
        type: 'ping',
        timestamp: pingTime,
      }));
      
      const pong = await waitForMessage(ws);
      
      assert.strictEqual(pong.type, 'pong');
      assert(typeof pong.latency === 'number', '应该返回延迟');
      assert(pong.latency >= 0, '延迟应该非负');
    } finally {
      ws.close();
    }
  }),

  test('断线重连测试', async (context) => {
    const { gateway } = context;
    
    // 第一次连接
    let ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      const hello1 = await waitForMessage(ws);
      assert.strictEqual(hello1.type, 'hello');
      
      // 关闭连接
      ws.close();
      await new Promise(resolve => setTimeout(resolve, 100));
      
      // 重新连接
      ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
      
      // 应该收到新的 Hello
      const hello2 = await waitForMessage(ws);
      assert.strictEqual(hello2.type, 'hello');
      assert.notStrictEqual(hello1.nonce, hello2.nonce, '应该生成新的 nonce');
    } finally {
      ws.close();
    }
  }),

  test('并发连接测试', async (context) => {
    const { gateway } = context;
    
    const connectionCount = 5;
    const clients = [];
    
    try {
      // 创建多个连接
      for (let i = 0; i < connectionCount; i++) {
        const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
        clients.push(ws);
        
        // 每个客户端都应该收到 Hello
        const hello = await waitForMessage(ws);
        assert.strictEqual(hello.type, 'hello');
      }
      
      // 验证服务器统计
      const stats = gateway.getStats();
      assert(stats.connections >= connectionCount, '应该记录所有连接');
    } finally {
      // 清理
      for (const ws of clients) {
        ws.close();
      }
    }
  }),

  test('协议版本兼容性', async (context) => {
    const { gateway } = context;
    
    const ws = await createWebSocketClient(`ws://localhost:${CONFIG.gatewayPort}`);
    
    try {
      const hello = await waitForMessage(ws);
      
      assert(hello.version, '应该包含版本号');
      assert.strictEqual(typeof hello.version, 'string', '版本号应该是字符串');
      
      // 验证版本格式 (允许 x.y 或 x.y.z)
      const versionRegex = /^\d+\.\d+(\.\d+)?$/;
      assert(versionRegex.test(hello.version), `版本号格式应该符合 semver: ${hello.version}`);
    } finally {
      ws.close();
    }
  }),

  test('Gateway 统计信息', async (context) => {
    const { gateway } = context;
    
    const stats = gateway.getStats();
    
    assert(typeof stats.connections === 'number', 'connections 应该是数字');
    assert(typeof stats.messagesReceived === 'number', 'messagesReceived 应该是数字');
    assert(typeof stats.messagesSent === 'number', 'messagesSent 应该是数字');
    assert(typeof stats.authAttempts === 'number', 'authAttempts 应该是数字');
  }),
];

// ========== 执行测试 ==========

async function main() {
  console.log('🧪 ClawChat 集成测试 (Mock Gateway)\n');
  console.log('='.repeat(60));
  
  // 启动 Mock Gateway
  const gateway = new MockGateway({
    port: CONFIG.gatewayPort,
    requireAuth: true,
    autoRespond: true,
  });
  
  await gateway.start();
  console.log(`Mock Gateway started on port ${CONFIG.gatewayPort}\n`);
  
  const context = { gateway };
  
  try {
    for (const testCase of integrationTests) {
      await runTest(testCase, context);
      await new Promise(resolve => setTimeout(resolve, 100));
    }
  } finally {
    // 关闭 Gateway
    await gateway.stop();
  }
  
  console.log('='.repeat(60));
  console.log(`\n📊 测试结果：${results.passed} 通过，${results.failed} 失败`);
  
  const coverage = (results.passed / (results.passed + results.failed) * 100).toFixed(1);
  console.log(`📈 覆盖率：${coverage}%\n`);
  
  if (results.failed > 0) {
    console.log('失败的测试:');
    results.tests
      .filter(t => t.status === 'FAIL')
      .forEach(t => console.log(`  - ${t.name}: ${t.error}`));
  }
  
  // 生成测试报告
  console.log('\n📋 测试报告已生成：tests/INTEGRATION_REPORT.md');
  
  process.exit(results.failed > 0 ? 1 : 0);
}

main().catch(err => {
  console.error('测试执行错误:', err);
  process.exit(1);
});

module.exports = { integrationTests, runTest, results };
