#!/usr/bin/env node
/**
 * ClawChat Gateway 协议 v3 兼容性测试
 *
 *   1. WebSocket 连接
 *   2. 等待 connect.challenge
 *   3. 用 Ed25519 密钥签名 v3 payload → 发送 connect 请求
 *   4. 接收 connect 响应
 *   5. 发送 chat.send
 *   6. 接收 assistant 响应
 */

const WebSocket = require('ws');
const crypto = require('crypto');

// ─── 配置 ───────────────────────────────────────────────
const GW_URL   = 'ws://localhost:18789/ws';
const GW_TOKEN = '989674d657564edbc29ef906489fba9e742f5b782273d331';
const TIMEOUT_MS = 30000;

// ─── 生成 Ed25519 密钥对 ─────────────────────────────────
const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');

const publicKeyPem  = publicKey.export({ type: 'spki', format: 'pem' }).toString();
const privateKeyPem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();

// Ed25519 SPKI DER 前缀 (12 bytes) — raw key 是后 32 bytes
const ED25519_SPKI_PREFIX = Buffer.from('302a300506032b6570032100', 'hex');

function derivePublicKeyRaw(pem) {
  const spki = crypto.createPublicKey(pem).export({ type: 'spki', format: 'der' });
  if (
    spki.length === ED25519_SPKI_PREFIX.length + 32 &&
    spki.subarray(0, ED25519_SPKI_PREFIX.length).equals(ED25519_SPKI_PREFIX)
  ) {
    return spki.subarray(ED25519_SPKI_PREFIX.length);
  }
  return spki;
}

// base64url 编码
function base64UrlEncode(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

// 公钥: raw 32 bytes → base64url
const publicKeyRaw = derivePublicKeyRaw(publicKeyPem);
const PUBLIC_KEY_B64URL = base64UrlEncode(publicKeyRaw);

// deviceId = sha256(raw public key).hex()
const DEVICE_ID = crypto.createHash('sha256').update(publicKeyRaw).digest('hex');

// Ed25519 签名: crypto.sign(null, ...) — 无需指定 hash
function sign(payload) {
  const key = crypto.createPrivateKey(privateKeyPem);
  return base64UrlEncode(crypto.sign(null, Buffer.from(payload, 'utf8'), key));
}

// ─── 连接参数 ────────────────────────────────────────────
const CLIENT_ID       = 'openclaw-android';
const CLIENT_VERSION  = '1.0.0';
const CLIENT_PLATFORM = 'android';
const CLIENT_MODE     = 'cli';
const ROLE            = 'operator';
const SCOPES          = ['operator.read', 'operator.write', 'operator.admin'];

// ─── 状态 ───────────────────────────────────────────────
const results = [];
let ws, timer;
let reqCounter = 0;
let chatSent = false;

function pass(step, detail) {
  results.push({ step, ok: true });
  console.log(`  ✅ PASS  ${step}${detail ? ' — ' + detail : ''}`);
}
function fail(step, detail) {
  results.push({ step, ok: false, detail });
  console.log(`  ❌ FAIL  ${step}${detail ? ' — ' + detail : ''}`);
}
function nextId(prefix) {
  return `${prefix}-${Date.now()}-${reqCounter++}`;
}

// ─── 主流程 ─────────────────────────────────────────────
console.log('\n🔌 Gateway Protocol v3 — Compatibility Test');
console.log(`   Target : ${GW_URL}`);
console.log(`   Device : ${DEVICE_ID.slice(0, 16)}…\n`);

timer = setTimeout(() => {
  fail('timeout', `no response within ${TIMEOUT_MS / 1000}s`);
  finish();
}, TIMEOUT_MS);

ws = new WebSocket(GW_URL, {
  headers: {
    'Authorization': `Bearer ${GW_TOKEN}`,
    'X-ClawChat-Protocol-Version': '3.0.0',
  },
});

ws.on('open', () => pass('1. WebSocket open'));
ws.on('error', (e) => { fail('WebSocket error', e.message); finish(); });
ws.on('close', (code, reason) => console.log(`\n   WebSocket closed: ${code} ${reason}`));

ws.on('message', (raw) => {
  const text = raw.toString();
  let msg;
  try { msg = JSON.parse(text); } catch { return; }

  // ── Step 2: connect.challenge ──
  if (msg.type === 'event' && msg.event === 'connect.challenge') {
    const nonce = msg.payload?.nonce;
    if (nonce) {
      pass('2. connect.challenge', `nonce=${nonce.slice(0, 8)}…`);
      sendConnect(nonce);
    } else {
      fail('2. connect.challenge', 'missing nonce');
      finish();
    }
    return;
  }

  // ── Step 3: connect response (type=res) ──
  if (msg.type === 'res' && msg.id?.startsWith('auth-')) {
    if (msg.ok) {
      pass('3. connect accepted', `payload keys: [${Object.keys(msg.payload || {}).join(', ')}]`);
      trySendChat();
    } else {
      fail('3. connect rejected', `${msg.error?.code}: ${msg.error?.message}`);
      if (msg.error?.details) {
        console.log(`      details: ${JSON.stringify(msg.error.details)}`);
      }
      finish();
    }
    return;
  }

  // ── Step 3 alt: connect.ok event ──
  if (msg.type === 'event' && msg.event === 'connect.ok') {
    pass('3. connect.ok event', `deviceToken=${(msg.payload?.deviceToken || '').slice(0, 12)}…`);
    trySendChat();
    return;
  }

  // ── connect.error event ──
  if (msg.type === 'event' && msg.event === 'connect.error') {
    fail('3. connect.error', `${msg.payload?.code}: ${msg.payload?.message}`);
    finish();
    return;
  }

  // ── Step 4: sessions.list response ──
  if (msg.type === 'res' && msg.id?.startsWith('list-')) {
    if (msg.ok) {
      const sessions = msg.payload?.sessions || [];
      const sessionKey = sessions[0]?.key || sessions[0]?.id || 'main';
      pass('4. sessions.list', `${sessions.length} sessions, using "${sessionKey}"`);
      sendChat(sessionKey);
    } else {
      console.log(`   ⚠ sessions.list failed (${msg.error?.message}), using "main"`);
      sendChat('main');
    }
    return;
  }

  // ── Step 5: chat.send response ──
  if (msg.type === 'res' && msg.id?.startsWith('chat-')) {
    if (msg.ok) {
      pass('5. chat.send accepted');
    } else {
      fail('5. chat.send rejected', `${msg.error?.code}: ${msg.error?.message}`);
      finish();
    }
    return;
  }

  // ── Step 6: assistant reply via chat event ──
  if (msg.type === 'event' && msg.event === 'chat') {
    const p = msg.payload || {};
    if (p.state === 'delta' && p.message?.content) {
      // streaming delta — extract text
      const parts = Array.isArray(p.message.content) ? p.message.content : [p.message.content];
      const text = parts.map(c => typeof c === 'string' ? c : c.text || '').join('');
      if (text) {
        const preview = text.slice(0, 80).replace(/\n/g, ' ');
        pass('6. assistant response (delta)', `"${preview}${text.length > 80 ? '…' : ''}"`);
        finish();
        return;
      }
    }
    if (p.state === 'final') {
      // final event — may contain message or just signal completion
      const parts = p.message?.content;
      if (parts) {
        const arr = Array.isArray(parts) ? parts : [parts];
        const text = arr.map(c => typeof c === 'string' ? c : c.text || '').join('');
        if (text) {
          const preview = text.slice(0, 80).replace(/\n/g, ' ');
          pass('6. assistant response (final)', `"${preview}${text.length > 80 ? '…' : ''}"`);
          finish();
          return;
        }
      }
      // final without inline content — still counts as success
      if (!results.some(r => r.step.startsWith('6.'))) {
        pass('6. chat completed', `runId=${p.runId || '?'}, state=final`);
      }
      finish();
      return;
    }
    // other chat states (queued, running, etc.) — log but don't act
    console.log(`   📨 chat state=${p.state || '?'} runId=${p.runId || '?'}`);
    return;
  }

  // ── Step 6 alt: session.message event (legacy) ──
  if (msg.type === 'event' && msg.event === 'session.message') {
    const content = msg.payload?.message?.content || msg.payload?.content || '';
    const preview = content.slice(0, 80).replace(/\n/g, ' ');
    pass('6. assistant response', `"${preview}${content.length > 80 ? '…' : ''}"`);
    finish();
    return;
  }

  // 其他
  console.log(`   📨 ${msg.type}/${msg.event || msg.id || '?'}: ${text.slice(0, 200)}`);
});

// ─── 发送 connect ────────────────────────────────────────
function sendConnect(nonce) {
  const signedAtMs = Date.now();

  // v3 签名 payload: "v3|{deviceId}|{clientId}|{clientMode}|{role}|{scopes}|{signedAtMs}|{token}|{nonce}|{platform}|{deviceFamily}"
  // - scopes: 逗号分隔
  // - token: gateway token (用于签名绑定)
  // - platform/deviceFamily: 小写 NFKD 去变音符
  const scopesStr = SCOPES.join(',');
  const payload = [
    'v3',
    DEVICE_ID,
    CLIENT_ID,
    CLIENT_MODE,
    ROLE,
    scopesStr,
    String(signedAtMs),
    GW_TOKEN,           // token 参与签名
    nonce,
    CLIENT_PLATFORM,    // platform
    '',                 // deviceFamily (空)
  ].join('|');

  const signature = sign(payload);

  const frame = {
    type: 'req',
    id: nextId('auth'),
    method: 'connect',
    params: {
      minProtocol: 3,
      maxProtocol: 3,
      client: {
        id: CLIENT_ID,
        version: CLIENT_VERSION,
        platform: CLIENT_PLATFORM,
        mode: CLIENT_MODE,
      },
      role: ROLE,
      scopes: SCOPES,
      caps: [],
      auth: {
        token: GW_TOKEN,
      },
      device: {
        id: DEVICE_ID,
        publicKey: PUBLIC_KEY_B64URL,
        signature,
        signedAt: signedAtMs,
        nonce,
      },
    },
  };
  ws.send(JSON.stringify(frame));
  console.log('   → sent connect request (Ed25519 v3 signed)');
}

// ─── 获取 session → 发送 chat ────────────────────────────
function trySendChat() {
  if (chatSent) return;
  chatSent = true;
  ws.send(JSON.stringify({
    type: 'req',
    id: nextId('list'),
    method: 'sessions.list',
    params: {},
  }));
  console.log('   → sent sessions.list');
}

function sendChat(sessionKey) {
  ws.send(JSON.stringify({
    type: 'req',
    id: nextId('chat'),
    method: 'chat.send',
    params: {
      sessionKey,
      message: 'Hello from ClawChat Android test! Reply with one word.',
      idempotencyKey: `idem-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    },
  }));
  console.log(`   → sent chat.send to "${sessionKey}"`);
}

// ─── 汇总 ───────────────────────────────────────────────
function finish() {
  clearTimeout(timer);
  console.log('\n── Summary ──────────────────────────────────');
  const passed = results.filter(r => r.ok).length;
  const failed = results.filter(r => !r.ok).length;
  results.forEach(r => console.log(`  ${r.ok ? '✅' : '❌'} ${r.step}`));
  console.log(`\n  Total: ${passed} passed, ${failed} failed\n`);
  try { ws.close(); } catch {}
  process.exit(failed > 0 ? 1 : 0);
}
