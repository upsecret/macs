// Swagger API test server — MACS 커넥터(API) 연동 검증 전용.
// 외부 의존성 0 (Swagger UI 는 CDN 에서 로드).
//
// 제공 경로:
//   GET  /v3/api-docs          → OpenAPI 3.0 JSON (커넥터 문서연동의 기본 경로)
//   GET  /swagger-ui  ( / )    → Swagger UI (CDN) — /v3/api-docs 를 렌더
//   GET  /health               → 헬스체크
//   GET  /api/products         → 목록 (query: inStock=true|false)
//   POST /api/products         → 생성
//   GET  /api/products/{id}    → 단건
//   DELETE /api/products/{id}  → 삭제
//
// 커넥터 등록 흐름: 경로설정에서 uri=http://swagger-api-server:8090,
//   Path=/api/products/** 라우트를 만들고 커넥터(type=api) 등록 →
//   포털 API 문서 뷰어가 /api/admin/connectors/{id}/api-docs (gateway proxy)
//   → http://swagger-api-server:8090/v3/api-docs 를 그대로 가져와 렌더.
'use strict';

const http = require('http');

const PORT = parseInt(process.env.PORT || '8090', 10);

/* ── in-memory 데이터 ─────────────────────────────────────── */
let products = [
  { id: 1, name: 'Mechanical Keyboard', price: 89.99, inStock: true },
  { id: 2, name: 'Wireless Mouse', price: 24.99, inStock: true },
  { id: 3, name: '27" Monitor', price: 219.0, inStock: false },
];
let nextId = 4;

/* ── OpenAPI 3.0 스펙 ─────────────────────────────────────── */
const OPENAPI = {
  openapi: '3.0.3',
  info: {
    title: 'Swagger API Test Server',
    version: '1.0.0',
    description:
      'MACS 커넥터(API) 연동 테스트용 샘플 서비스. 게이트웨이 라우트를 통해 호출되며 ' +
      'OpenAPI JSON 은 /v3/api-docs 에서 제공된다.',
  },
  servers: [{ url: '/', description: 'gateway 경유 (Path 라우트)' }],
  tags: [
    { name: 'Products', description: '상품 CRUD 샘플' },
    { name: 'System', description: '헬스/메타' },
  ],
  paths: {
    '/health': {
      get: {
        tags: ['System'],
        operationId: 'health',
        summary: '헬스 체크',
        responses: {
          '200': {
            description: 'OK',
            content: { 'text/plain': { schema: { type: 'string', example: 'ok' } } },
          },
        },
      },
    },
    '/api/products': {
      get: {
        tags: ['Products'],
        operationId: 'listProducts',
        summary: '상품 목록 조회',
        parameters: [
          {
            name: 'inStock',
            in: 'query',
            required: false,
            description: '재고 보유 여부로 필터',
            schema: { type: 'boolean' },
          },
        ],
        responses: {
          '200': {
            description: '상품 배열',
            content: {
              'application/json': {
                schema: { type: 'array', items: { $ref: '#/components/schemas/Product' } },
              },
            },
          },
        },
      },
      post: {
        tags: ['Products'],
        operationId: 'createProduct',
        summary: '상품 생성',
        requestBody: {
          required: true,
          content: {
            'application/json': { schema: { $ref: '#/components/schemas/NewProduct' } },
          },
        },
        responses: {
          '201': {
            description: '생성된 상품',
            content: {
              'application/json': { schema: { $ref: '#/components/schemas/Product' } },
            },
          },
          '400': {
            description: '잘못된 요청',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } },
          },
        },
      },
    },
    '/api/products/{id}': {
      get: {
        tags: ['Products'],
        operationId: 'getProduct',
        summary: '상품 단건 조회',
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer', format: 'int64' } },
        ],
        responses: {
          '200': {
            description: '상품',
            content: {
              'application/json': { schema: { $ref: '#/components/schemas/Product' } },
            },
          },
          '404': {
            description: '없음',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } },
          },
        },
      },
      delete: {
        tags: ['Products'],
        operationId: 'deleteProduct',
        summary: '상품 삭제',
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'integer', format: 'int64' } },
        ],
        responses: {
          '204': { description: '삭제됨' },
          '404': {
            description: '없음',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Error' } } },
          },
        },
      },
    },
  },
  components: {
    schemas: {
      Product: {
        type: 'object',
        required: ['id', 'name', 'price', 'inStock'],
        properties: {
          id: { type: 'integer', format: 'int64', example: 1 },
          name: { type: 'string', example: 'Mechanical Keyboard' },
          price: { type: 'number', format: 'double', example: 89.99 },
          inStock: { type: 'boolean', example: true },
        },
      },
      NewProduct: {
        type: 'object',
        required: ['name', 'price'],
        properties: {
          name: { type: 'string', example: 'New Gadget' },
          price: { type: 'number', format: 'double', example: 49.99 },
          inStock: { type: 'boolean', default: true },
        },
      },
      Error: {
        type: 'object',
        properties: {
          error: { type: 'string', example: 'Not Found' },
          message: { type: 'string', example: 'product 99 not found' },
        },
      },
    },
  },
};

/* ── Swagger UI (CDN) ─────────────────────────────────────── */
const SWAGGER_HTML = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Swagger API Test Server</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js" crossorigin></script>
  <script>
    window.ui = SwaggerUIBundle({
      url: '/v3/api-docs',
      dom_id: '#swagger-ui',
      deepLinking: true,
    });
  </script>
</body>
</html>`;

/* ── helpers ──────────────────────────────────────────────── */
function sendJson(res, code, body) {
  const payload = JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => {
      data += c;
      if (data.length > 1e6) req.destroy();
    });
    req.on('end', () => {
      if (!data) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch {
        resolve(null); // 파싱 실패 표시
      }
    });
  });
}

/* ── server ───────────────────────────────────────────────── */
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;
  const method = req.method;

  // health
  if (method === 'GET' && path === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }

  // OpenAPI JSON (문서연동 기본 경로)
  if (method === 'GET' && (path === '/v3/api-docs' || path === '/openapi.json')) {
    sendJson(res, 200, OPENAPI);
    return;
  }

  // Swagger UI
  if (method === 'GET' && (path === '/' || path === '/swagger-ui' || path === '/swagger-ui/' || path === '/docs')) {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(SWAGGER_HTML);
    return;
  }

  // /api/products
  if (path === '/api/products') {
    if (method === 'GET') {
      const inStock = url.searchParams.get('inStock');
      let list = products;
      if (inStock === 'true') list = products.filter((p) => p.inStock);
      else if (inStock === 'false') list = products.filter((p) => !p.inStock);
      sendJson(res, 200, list);
      return;
    }
    if (method === 'POST') {
      const body = await readBody(req);
      if (body === null) {
        sendJson(res, 400, { error: 'Bad Request', message: 'invalid JSON body' });
        return;
      }
      if (!body.name || typeof body.price !== 'number') {
        sendJson(res, 400, { error: 'Bad Request', message: 'name and numeric price are required' });
        return;
      }
      const created = {
        id: nextId++,
        name: body.name,
        price: body.price,
        inStock: body.inStock !== false,
      };
      products.push(created);
      sendJson(res, 201, created);
      return;
    }
  }

  // /api/products/{id}
  const m = path.match(/^\/api\/products\/(\d+)$/);
  if (m) {
    const id = parseInt(m[1], 10);
    const idx = products.findIndex((p) => p.id === id);
    if (method === 'GET') {
      if (idx < 0) {
        sendJson(res, 404, { error: 'Not Found', message: `product ${id} not found` });
        return;
      }
      sendJson(res, 200, products[idx]);
      return;
    }
    if (method === 'DELETE') {
      if (idx < 0) {
        sendJson(res, 404, { error: 'Not Found', message: `product ${id} not found` });
        return;
      }
      products.splice(idx, 1);
      res.writeHead(204);
      res.end();
      return;
    }
  }

  sendJson(res, 404, { error: 'Not Found', message: `no handler for ${method} ${path}` });
});

server.listen(PORT, () => {
  console.log(`[swagger-api] listening on :${PORT}  (docs: /v3/api-docs, ui: /swagger-ui)`);
});
