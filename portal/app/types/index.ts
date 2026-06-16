export interface PermissionEntry {
  system: string;
  connector: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  employee_number: string;
  client_app: string;
}

export interface UserPermissionsResponse {
  appName: string;
  employeeNumber: string;
  permissions: PermissionEntry[];
}

export interface Permission {
  appName: string;
  employeeNumber: string;
  system: string;
  connector: string;
  role: string;
  createdAt: string;
}

export interface GatewayDefinition {
  name: string;
  args: Record<string, string>;
}

export interface RouteDefinition {
  id: string;
  uri: string;
  predicates: GatewayDefinition[];
  filters: GatewayDefinition[];
  order: number;
}

export interface ConfigProperty {
  application: string;
  profile: string;
  label: string;
  propKey: string;
  propValue: string;
}

export type ConnectorType = "agent" | "api" | "mcp";

export interface Connector {
  id: string;
  title: string;
  description: string | null;
  type: ConnectorType;
  /** 소속 시스템 (권한 모델 v2 의 system 과 일관). 예: common, rms, fdc */
  system: string;
  active: boolean;
  uri: string | null;
  /** optional 외부 OpenAPI JSON URL. null 이면 gateway /v3/api-docs/{id} 사용 */
  docsUrl: string | null;
  createdAt: string;
}

export interface AvailableRoute {
  id: string;
  uri: string;
}

/* ── MCP (Model Context Protocol) ───────────────────────── */

export type McpTransport = "streamable-http";
export type McpAuthType = "none" | "bearer";

export interface McpServer {
  id: string;
  name: string;
  description: string | null;
  endpointUrl: string;
  transport: McpTransport;
  authType: McpAuthType;
  hasAuthToken: boolean;
  system: string;
  createdAt: string;
}

export interface McpTool {
  name: string;
  description: string;
  /** JSON Schema for the tool's arguments (object). */
  inputSchema: unknown;
}

export interface McpContentBlock {
  type: string;        // "text" | "image" | "resource" | ...
  text?: string;
  data?: string;       // base64 for image
  mimeType?: string;
  resource?: unknown;
  [k: string]: unknown;
}

export interface McpToolCallResponse {
  content: McpContentBlock[];
  error: boolean;
  raw: unknown;
}
