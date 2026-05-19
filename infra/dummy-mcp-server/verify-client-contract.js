// McpJsonRpcClient (Java) 가 실제로 보낼 요청 형태를 시뮬레이션해
// dummy-mcp-server 가 그것을 정상 처리하는지 검증.
// 실행: node verify-client-contract.js  (server.js 가 :8765 에 떠 있어야 함)
'use strict';

const http = require('http');

function rpc(method, params, { bearer } = {}) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ jsonrpc: '2.0', id: Math.floor(Math.random() * 1e9), method, params });
    const headers = {
      // McpJsonRpcClient 가 RestClient 에서 보내는 헤더 set
      'Content-Type': 'application/json',
      'Accept': 'application/json, text/event-stream',
      'Content-Length': Buffer.byteLength(body),
    };
    if (bearer) headers['Authorization'] = `Bearer ${bearer}`;
    const req = http.request({
      hostname: '127.0.0.1', port: 8765, path: '/mcp', method: 'POST', headers,
    }, (res) => {
      let buf = '';
      res.on('data', (c) => (buf += c));
      res.on('end', () => resolve({ status: res.statusCode, contentType: res.headers['content-type'], body: buf }));
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function expect(label, cond, detail) {
  const tag = cond ? 'PASS' : 'FAIL';
  console.log(`[${tag}] ${label}${detail ? ' — ' + detail : ''}`);
  if (!cond) process.exitCode = 1;
}

(async () => {
  console.log('— verifying dummy-mcp-server against Java client contract —');

  // 1. initialize
  const init = await rpc('initialize', {
    protocolVersion: '2025-03-26',
    capabilities: {},
    clientInfo: { name: 'macs-admin-server', version: '1.0.0' },
  });
  const initObj = JSON.parse(init.body);
  expect('initialize returns 200', init.status === 200);
  expect('initialize result.protocolVersion is 2025-03-26',
         initObj.result && initObj.result.protocolVersion === '2025-03-26');
  expect('initialize serverInfo.name is dummy-mcp',
         initObj.result.serverInfo && initObj.result.serverInfo.name === 'dummy-mcp');

  // 2. tools/list
  const list = await rpc('tools/list', {});
  const listObj = JSON.parse(list.body);
  const tools = listObj.result && listObj.result.tools;
  expect('tools/list returns array', Array.isArray(tools));
  expect('echo tool present', tools.some((t) => t.name === 'echo'));
  expect('add tool present', tools.some((t) => t.name === 'add'));
  const echo = tools.find((t) => t.name === 'echo');
  expect('echo has JSON Schema inputSchema',
         echo && echo.inputSchema && echo.inputSchema.type === 'object');

  // 3. tools/call echo
  const call1 = await rpc('tools/call', { name: 'echo', arguments: { message: 'hi' } });
  const c1 = JSON.parse(call1.body);
  expect('echo call returns content[]',
         c1.result && Array.isArray(c1.result.content));
  expect('echo content[0].type === text',
         c1.result.content[0] && c1.result.content[0].type === 'text');
  expect('echo content[0].text === "hi"',
         c1.result.content[0].text === 'hi');

  // 4. tools/call add
  const call2 = await rpc('tools/call', { name: 'add', arguments: { a: 100, b: 23 } });
  const c2 = JSON.parse(call2.body);
  expect('add returns sum text', c2.result.content[0].text === 'Sum = 123');

  // 5. tools/call unknown → JSON-RPC error
  const call3 = await rpc('tools/call', { name: 'nope', arguments: {} });
  const c3 = JSON.parse(call3.body);
  expect('unknown tool returns error.code === -32601',
         c3.error && c3.error.code === -32601);
  expect('unknown tool error.message mentions name',
         c3.error.message && c3.error.message.includes('nope'));

  // 6. Bearer 헤더가 무시되어도 정상 동작 (dummy 는 auth 검증 안 함)
  const bearer = await rpc('initialize', {
    protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'x', version: '1' },
  }, { bearer: 'fake-token-xyz' });
  expect('initialize with Bearer header still succeeds', bearer.status === 200);

  // 7. invalid JSON → JSON-RPC parse error
  await new Promise((resolve, reject) => {
    const req = http.request({
      hostname: '127.0.0.1', port: 8765, path: '/mcp', method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    }, (res) => {
      let buf = '';
      res.on('data', (c) => (buf += c));
      res.on('end', () => {
        const obj = JSON.parse(buf);
        expect('malformed body returns parse error -32700',
               obj.error && obj.error.code === -32700);
        resolve();
      });
    });
    req.on('error', reject);
    req.write('not json');
    req.end();
  });

  console.log('— done —');
})();
