// Dummy upstream API server — MACS E2E 검증 전용.
// 외부 의존성 0. 모든 경로에서 200 + 받은 요청(method/path/headers)을 echo.
// gateway 가 헤더(employee_number/app_name 등)를 정상 전달·StripPrefix 하는지 확인용.
'use strict';

const http = require('http');

const PORT = parseInt(process.env.PORT || '8088', 10);

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && (req.url === '/health' || req.url === '/')) {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  // 그 외 모든 요청 → 받은 내용 그대로 echo (업스트림 도달 = 권한/라우팅 통과 증거)
  const echo = {
    service: 'dummy-api-server',
    method: req.method,
    path: req.url,
    headers: {
      app_name: req.headers['app_name'] || null,
      employee_number: req.headers['employee_number'] || null,
      authorization: req.headers['authorization'] ? 'present' : null,
    },
  };
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(echo));
});

server.listen(PORT, () => {
  console.log(`[dummy-api] listening on :${PORT}`);
});
