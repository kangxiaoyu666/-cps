import client, { type ApiEnvelope } from "./request";

export type AffiliatePlatform = "MEITUAN" | "ELEME";

export interface ConfigurationWrite {
  displayName: string;
  appKey: string;
  appSecret: string;
  activityId: string;
  pid: string;
  sid: string | null;
  permissions: string[];
  enabled: boolean;
}

export interface ConfigurationView {
  id: number;
  platform: AffiliatePlatform;
  displayName: string;
  status: "ACTIVE" | "DISABLED";
  secretConfigured: boolean;
  appKeyMasked: string | null;
  activityId: string | null;
  pid: string | null;
  sid: string | null;
  permissions: string[];
  configuredAt: string | null;
  lastValidatedAt: string | null;
  lastValidationResult: string | null;
  updatedAt: string;
  version: number;
}

export interface ValidationResult {
  valid: boolean;
  code: string;
  message: string;
}

export interface SyncResult {
  jobId: number;
  scannedCount: number;
  successCount: number;
  continuationRequired: boolean;
}

export async function listAffiliateConfigurations(): Promise<ConfigurationView[]> {
  const { data } = await client.get<ApiEnvelope<ConfigurationView[]>>("/affiliate-configurations");
  return data.data;
}

export async function getAffiliateConfiguration(platform: AffiliatePlatform): Promise<ConfigurationView> {
  const { data } = await client.get<ApiEnvelope<ConfigurationView>>(`/affiliate-configurations/${platform}`);
  return data.data;
}

export async function saveAffiliateConfiguration(
  platform: AffiliatePlatform,
  input: ConfigurationWrite,
): Promise<ConfigurationView> {
  const { data } = await client.put<ApiEnvelope<ConfigurationView>>(`/affiliate-configurations/${platform}`, input);
  return data.data;
}

export async function deleteAffiliateConfiguration(platform: AffiliatePlatform): Promise<void> {
  await client.delete(`/affiliate-configurations/${platform}`);
}

export async function validateAffiliateConfiguration(platform: AffiliatePlatform): Promise<ValidationResult> {
  const { data } = await client.post<ApiEnvelope<ValidationResult>>(`/channels/${platform}/validate`);
  return data.data;
}

export async function syncAffiliateOrders(platform: AffiliatePlatform): Promise<SyncResult> {
  const { data } = await client.post<ApiEnvelope<SyncResult>>(`/orders/sync/${platform}`);
  return data.data;
}
