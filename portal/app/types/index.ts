export interface PermissionEntry {
  system: string;
  connector: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  app_name: string;
  employee_number: string;
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
  createdAt: string;
}

export interface AvailableRoute {
  id: string;
  uri: string;
}
