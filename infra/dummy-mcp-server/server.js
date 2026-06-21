// Dummy MCP server — JSON-RPC 2.0 over HTTP (Streamable HTTP, JSON response mode).
// MACS Phase 1 검증 전용. 외부 의존성 0 — 그대로 node server.js 로 기동.
'use strict';

const http = require('http');
const crypto = require('crypto');

const PORT = parseInt(process.env.PORT || '8765', 10);
const PROTOCOL_VERSION = '2025-03-26';
// 선택: 설정 시 MCP 서버가 자체적으로 Authorization: Bearer <token> 을 강제.
// 게이트웨이가 AuthValidation 을 안 걸고 업스트림 bearer 로만 보호하는 경우 검증용.
const BEARER_TOKEN = process.env.MCP_BEARER_TOKEN || '';
// 선택: 설정 시 Streamable HTTP 세션을 강제(stateful). initialize 응답으로 Mcp-Session-Id 발급,
// 이후 요청은 그 헤더가 없으면 400, 모르는 값이면 404. (게이트웨이 세션 처리 검증용)
const STATEFUL = (process.env.MCP_STATEFUL || '').toLowerCase() === 'true';
const sessions = new Set();

const TOOLS = [
  {
    name: 'echo',
    description: 'Echoes back the provided message.',
    inputSchema: {
      type: 'object',
      properties: { message: { type: 'string', description: 'Text to echo back' } },
      required: ['message'],
    },
  },
  {
    name: 'add',
    description: 'Adds two numbers and returns the sum.',
    inputSchema: {
      type: 'object',
      properties: {
        a: { type: 'number', description: 'first addend' },
        b: { type: 'number', description: 'second addend' },
      },
      required: ['a', 'b'],
    },
  },
];

function handle(method, params) {
  if (method === 'initialize') {
    return {
      protocolVersion: PROTOCOL_VERSION,
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: 'dummy-mcp', version: '1.0.0' },
    };
  }
  if (method === 'notifications/initialized') {
    return null; // notification — no response body
  }
  if (method === 'tools/list') {
    return { tools: TOOLS };
  }
  if (method === 'tools/call') {
    const name = params && params.name;
    const args = (params && params.arguments) || {};
    if (name === 'echo') {
      const msg = args.message == null ? '' : String(args.message);
      return { content: [{ type: 'text', text: msg }] };
    }
    if (name === 'add') {
      const a = Number(args.a);
      const b = Number(args.b);
      if (Number.isNaN(a) || Number.isNaN(b)) {
        return { content: [{ type: 'text', text: 'a and b must be numbers' }], isError: true };
      }
      return { content: [{ type: 'text', text: `Sum = ${a + b}` }] };
    }
    const err = new Error(`Unknown tool: ${name}`);
    err.code = -32601;
    throw err;
  }
  const err = new Error(`Method not found: ${method}`);
  err.code = -32601;
  throw err;
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && (req.url === '/health' || req.url === '/')) {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  if (req.method !== 'POST') {
    res.writeHead(405, { 'Content-Type': 'text/plain' });
    res.end('method not allowed');
    return;
  }
  // 자체 bearer 인증 (env 로 켰을 때만). 게이트웨이가 토큰을 그대로 전달했는지 검증.
  if (BEARER_TOKEN) {
    const auth = req.headers['authorization'] || '';
    if (auth !== `Bearer ${BEARER_TOKEN}`) {
      console.log(`[dummy-mcp] 401 — bad/absent bearer (got: ${auth ? 'present' : 'none'})`);
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        jsonrpc: '2.0', id: null,
        error: { code: -32001, message: 'Unauthorized: invalid bearer token' },
      }));
      return;
    }
  }
  let body = '';
  req.on('data', (chunk) => (body += chunk));
  req.on('end', () => {
    let rpc;
    try {
      rpc = JSON.parse(body);
    } catch (_) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        jsonrpc: '2.0', id: null,
        error: { code: -32700, message: 'Parse error' },
      }));
      return;
    }
    const id = rpc && Object.prototype.hasOwnProperty.call(rpc, 'id') ? rpc.id : null;
    const method = rpc && rpc.method;

    // stateful 세션 강제 (env 로 켰을 때만).
    let newSessionId = null;
    if (STATEFUL) {
      const sid = req.headers['mcp-session-id'];
      if (method === 'initialize') {
        newSessionId = crypto.randomUUID();
        sessions.add(newSessionId);
        console.log(`[dummy-mcp] session opened ${newSessionId}`);
      } else if (!sid) {
        console.log(`[dummy-mcp] 400 — missing Mcp-Session-Id (method=${method})`);
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          jsonrpc: '2.0', id,
          error: { code: -32600, message: 'Missing Mcp-Session-Id header' },
        }));
        return;
      } else if (!sessions.has(sid)) {
        console.log(`[dummy-mcp] 404 — unknown/expired session ${sid}`);
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          jsonrpc: '2.0', id,
          error: { code: -32001, message: 'Unknown or expired session' },
        }));
        return;
      }
    }

    try {
      const result = handle(method, rpc && rpc.params);
      // notification or method that returns null → 202 Accepted, empty body.
      if (id === null || id === undefined || result === null) {
        res.writeHead(202, newSessionId ? { 'Mcp-Session-Id': newSessionId } : {});
        res.end();
        return;
      }
      const headers = { 'Content-Type': 'application/json' };
      if (newSessionId) headers['Mcp-Session-Id'] = newSessionId; // initialize 응답에 세션 발급
      res.writeHead(200, headers);
      res.end(JSON.stringify({ jsonrpc: '2.0', id, result }));
    } catch (err) {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        jsonrpc: '2.0', id,
        error: { code: err.code || -32000, message: err.message || 'Server error' },
      }));
    }
  });
});

server.listen(PORT, () => {
  console.log(`[dummy-mcp] listening on :${PORT} (protocol ${PROTOCOL_VERSION})`);
});
