export interface AuthResponse {
  token: string;
  system: string;
  employee_number: string;
  group: string;
  allowed_resources_list: string[];
}

export interface SystemConnector {
  systemName: string;
  connectorName: string;
  description: string;
}

export interface GroupInfo {
  groupId: number;
  systemName: string;
  groupName: string;
}

export interface GroupMember {
  groupId: number;
  employeeNumber: string;
}

export interface GroupResource {
  groupId: number;
  resourceName: string;
}

export interface UserResource {
  employeeNumber: string;
  systemName: string;
  resourceName: string;
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
