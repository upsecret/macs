export interface PermissionEntry {
  system: string;
  connector: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  employee_number: string;
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
