#!/usr/bin/env node
/**
 * Mock Gateway Server for ClawChat Integration Tests
 * 
 * 模拟 OpenClaw Gateway 协议 v3
 * 功能：
 * - WebSocket 监听 (端口 18789)
 * - 发送 Hello (含 nonce)
 * - 验证客户端签名
 * - 返回 AuthResponse
 * - 发送测试消息
 * - 接收客户端消息
 */

const WebSocket = require('ws');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');

class MockGateway {
  constructor(options = {}) {
    this.port = options.port || 18789;
    this.server = null;
    this.wss = null;
    this.clients = new Map();
    this.messageQueue = [];
    this.stats = {
      connections: 0,
      messagesReceived: 0,
      messagesSent: 0,
      authAttempts: 0,
      authSuccess: 0,
      authFailed: 0,
    };
    
    // 测试配置
    this.config = {
      requireAuth: options.requireAuth ?? true,
      validPublicKeys: options.validPublicKeys || [],  // 允许的公钥列表
      autoRespond: options.autoRespond ?? true,
      testMessages: options.testMessages || [],
    };
  }

  /**
   * 启动 Mock Gateway
   */
  async start() {
    return new Promise((resolve, reject) => {
      this.server = require('http').createServer();
      this.wss = new WebSocket.Server({ server: this.server });

      this.wss.on('connection', (ws, req) => {
        this._handleConnection(ws, req);
      });

      this.wss.on('error', (err) => {
        console.error('[MockGateway] WebSocket error:', err);
        reject(err);
      });

      this.server.listen(this.port, () => {
        console.log(`[MockGateway] Listening on port ${this.port}`);
        resolve(this);
      });

      this.server.on('error', reject);
    });
  }

  /**
   * 停止 Mock Gateway
   */
  async stop() {
    return new Promise((resolve) => {
      if (this.wss) {
        this.wss.close(() => {
          console.log('[MockGateway] WebSocket server closed');
        });
      }
      
      if (this.server) {
        this.server.close(() => {
          console.log('[MockGateway] HTTP server closed');
          resolve();
        });
      } else {
        resolve();
      }
    });
  }

  /**
   * 处理新连接
   */
  _handleConnection(ws, req) {
    const clientId = uuidv4();
    this.stats.connections++;
    
    console.log(`[MockGateway] Client connected: ${clientId}`);
    
    const clientState = {
      id: clientId,
      ws,
      authenticated: false,
      publicKey: null,
      sessionId: null,
    };
    
    this.clients.set(clientId, clientState);

    // 发送 Hello 消息 (含 nonce)
    this._sendHello(ws, clientId);

    ws.on('message', (data) => {
      this._handleMessage(clientId, data);
    });

    ws.on('close', () => {
      console.log(`[MockGateway] Client disconnected: ${clientId}`);
      this.clients.delete(clientId);
    });

    ws.on('error', (err) => {
      console.error(`[MockGateway] Client error ${clientId}:`, err);
    });
  }

  /**
   * 发送 Hello 消息
   */
  _sendHello(ws, clientId) {
    const nonce = crypto.randomBytes(32).toString('hex');
    
    const helloMessage = {
      type: 'hello',
      version: '3.0',
      serverId: 'mock-gateway',
      nonce,
      timestamp: Date.now(),
    };

    // 保存 nonce 用于后续验证
    const client = this.clients.get(clientId);
    if (client) {
      client.pendingNonce = nonce;
    }

    this._send(ws, helloMessage);
    console.log(`[MockGateway] Sent Hello to ${clientId} (nonce: ${nonce.substring(0, 16)}...)`);
  }

  /**
   * 处理客户端消息
   */
  async _handleMessage(clientId, data) {
    const client = this.clients.get(clientId);
    if (!client) return;

    this.stats.messagesReceived++;

    try {
      const message = JSON.parse(data.toString());
      console.log(`[MockGateway] Received from ${clientId}:`, message.type);

      switch (message.type) {
        case 'auth':
          await this._handleAuth(clientId, message);
          break;
        
        case 'session/create':
          await this._handleSessionCreate(clientId, message);
          break;
        
        case 'session/list':
          await this._handleSessionList(clientId, message);
          break;
        
        case 'message':
          await this._handleClientMessage(clientId, message);
          break;
        
        case 'ping':
          await this._handlePing(clientId, message);
          break;
        
        default:
          console.log(`[MockGateway] Unknown message type: ${message.type}`);
      }
    } catch (err) {
      console.error(`[MockGateway] Parse error from ${clientId}:`, err.message);
      this._sendError(clientId, 'invalid_json', err.message);
    }
  }

  /**
   * 处理认证请求
   */
  async _handleAuth(clientId, message) {
    this.stats.authAttempts++;
    const client = this.clients.get(clientId);
    
    if (!client || !client.pendingNonce) {
      this._sendError(clientId, 'auth_failed', 'No pending nonce');
      this.stats.authFailed++;
      return;
    }

    const { publicKey, signature } = message;
    
    // 验证签名
    const isValid = this._verifySignature(
      client.pendingNonce,
      publicKey,
      signature
    );

    if (isValid || !this.config.requireAuth) {
      client.authenticated = true;
      client.publicKey = publicKey;
      client.sessionId = `session_${uuidv4()}`;
      this.stats.authSuccess++;
      
      console.log(`[MockGateway] Auth success for ${clientId}`);
      
      this._send(client.ws, {
        type: 'auth_response',
        status: 'success',
        sessionId: client.sessionId,
        timestamp: Date.now(),
      });
    } else {
      this.stats.authFailed++;
      console.log(`[MockGateway] Auth failed for ${clientId}`);
      
      this._send(client.ws, {
        type: 'auth_response',
        status: 'failed',
        reason: 'invalid_signature',
        timestamp: Date.now(),
      });
    }

    // 清除 nonce
    client.pendingNonce = null;
  }

  /**
   * 处理会话创建
   */
  async _handleSessionCreate(clientId, message) {
    const client = this.clients.get(clientId);
    
    if (!client?.authenticated) {
      this._sendError(clientId, 'unauthorized', 'Authentication required');
      return;
    }

    const sessionId = `session_${uuidv4()}`;
    
    this._send(client.ws, {
      type: 'session_created',
      sessionId,
      model: message.model || 'default',
      timestamp: Date.now(),
    });

    // 发送测试消息
    if (this.config.autoRespond) {
      setTimeout(() => {
        this._send(client.ws, {
          type: 'message',
          sessionId,
          role: 'assistant',
          content: 'Hello! I am the Mock Gateway. How can I help you?',
          timestamp: Date.now(),
        });
        this.stats.messagesSent++;
      }, 500);
    }
  }

  /**
   * 处理会话列表
   */
  async _handleSessionList(clientId, message) {
    const client = this.clients.get(clientId);
    
    if (!client?.authenticated) {
      this._sendError(clientId, 'unauthorized', 'Authentication required');
      return;
    }

    this._send(client.ws, {
      type: 'session_list',
      sessions: [
        {
          id: client.sessionId,
          label: 'Test Session',
          model: 'qwen3.5-plus',
          status: 'running',
          lastActivityAt: Date.now(),
          messageCount: 0,
        },
      ],
      timestamp: Date.now(),
    });
  }

  /**
   * 处理客户端消息
   */
  async _handleClientMessage(clientId, message) {
    const client = this.clients.get(clientId);
    
    if (!client?.authenticated) {
      this._sendError(clientId, 'unauthorized', 'Authentication required');
      return;
    }

    console.log(`[MockGateway] Message from ${clientId}: ${message.content?.substring(0, 50)}`);

    // 自动响应
    if (this.config.autoRespond) {
      setTimeout(() => {
        this._send(client.ws, {
          type: 'message',
          sessionId: message.sessionId || client.sessionId,
          role: 'assistant',
          content: `Echo: ${message.content}`,
          timestamp: Date.now(),
        });
        this.stats.messagesSent++;
      }, 300);
    }
  }

  /**
   * 处理 Ping
   */
  async _handlePing(clientId, message) {
    const client = this.clients.get(clientId);
    
    this._send(client.ws, {
      type: 'pong',
      timestamp: Date.now(),
      latency: message.timestamp ? Date.now() - message.timestamp : 0,
    });
  }

  /**
   * 发送消息
   */
  _send(ws, message) {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(message));
      this.stats.messagesSent++;
    }
  }

  /**
   * 发送错误
   */
  _sendError(clientId, code, message) {
    const client = this.clients.get(clientId);
    if (client) {
      this._send(client.ws, {
        type: 'error',
        code,
        message,
        timestamp: Date.now(),
      });
    }
  }

  /**
   * 验证签名 (简化版 - 实际应使用公钥加密验证)
   */
  _verifySignature(nonce, publicKey, signature) {
    // 简化验证：接受任何非空签名用于测试
    // 实际实现应使用 crypto.verify()
    if (!this.config.requireAuth) return true;
    
    // 如果有预配置的公钥，检查是否匹配
    if (this.config.validPublicKeys.length > 0) {
      return this.config.validPublicKeys.includes(publicKey);
    }
    
    // 默认接受任何签名 (测试模式)
    return !!signature && signature.length > 0;
  }

  /**
   * 获取统计信息
   */
  getStats() {
    return {
      ...this.stats,
      activeClients: this.clients.size,
    };
  }

  /**
   * 广播消息到所有客户端
   */
  broadcast(message) {
    const data = JSON.stringify(message);
    for (const [clientId, client] of this.clients) {
      if (client.ws.readyState === WebSocket.OPEN) {
        client.ws.send(data);
      }
    }
  }

  /**
   * 向特定客户端发送消息
   */
  sendToClient(clientId, message) {
    const client = this.clients.get(clientId);
    if (client && client.ws.readyState === WebSocket.OPEN) {
      this._send(client.ws, message);
      return true;
    }
    return false;
  }
}

// ========== CLI 入口 ==========

async function main() {
  console.log('🚀 Mock Gateway Server Starting...\n');
  
  const gateway = new MockGateway({
    port: process.env.PORT ? parseInt(process.env.PORT) : 18789,
    requireAuth: process.env.REQUIRE_AUTH === 'true',
    autoRespond: true,
  });

  await gateway.start();
  
  console.log('\n📊 Mock Gateway Ready!');
  console.log('   Port: 18789');
  console.log('   Protocol: OpenClaw Gateway v3');
  console.log('\nPress Ctrl+C to stop\n');

  // 优雅关闭
  process.on('SIGINT', async () => {
    console.log('\n[MockGateway] Shutting down...');
    console.log('Final stats:', gateway.getStats());
    await gateway.stop();
    process.exit(0);
  });
}

// 如果直接运行则启动服务器
if (require.main === module) {
  main().catch(err => {
    console.error('Failed to start Mock Gateway:', err);
    process.exit(1);
  });
}

module.exports = { MockGateway };
