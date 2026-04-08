export interface AuthResponse {
  token: string;
  app_name: string;
  employee_number: string;
  group: string;
  allowed_resources_list: string[];
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

export interface AppInfo {
  appId: string;
  appName: string;
  description: string;
  createdAt: string;
}

export interface GroupInfo {
  groupId: string;
  appId: string;
  groupName: string;
}

export interface GroupMember {
  groupId: string;
  employeeNumber: string;
}

export interface GroupResource {
  groupId: string;
  resourceName: string;
}

export interface UserResource {
  employeeNumber: string;
  appId: string;
  resourceName: string;
}
