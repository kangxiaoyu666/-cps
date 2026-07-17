import axios from "axios";

export interface PageResult<T> { items: T[]; page: number; pageSize: number; total: number }
export interface ApiEnvelope<T> { code: string; message: string; data: T; requestId: string }

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1/admin",
  timeout: 10000,
  withCredentials: true
});

client.interceptors.request.use((config) => {
  const csrf = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1];
  if (csrf) config.headers["X-XSRF-TOKEN"] = decodeURIComponent(csrf);
  return config;
});
client.interceptors.response.use((response) => response, (error) => Promise.reject(new Error(error.response?.data?.message || "请求失败，请稍后重试")));

export async function getPage(resource: string, params: Record<string, unknown>): Promise<PageResult<Record<string, unknown>>> {
  const { data } = await client.get<ApiEnvelope<PageResult<Record<string, unknown>>>>(`/${resource}`, { params });
  return data.data;
}

export interface DashboardSummary {
  tenantId: number;
  ordersToday: number;
  pendingWithdrawals: number;
}

export async function getDashboard(): Promise<DashboardSummary> {
  const { data } = await client.get<ApiEnvelope<DashboardSummary>>("/dashboard");
  return data.data;
}

export async function login(tenantCode: string, username: string, password: string): Promise<{ displayName: string; role: string }> {
  const { data } = await client.post<ApiEnvelope<{ displayName: string; role: string }>>("/auth/login", { tenantCode, username, password });
  return data.data;
}

export default client;
