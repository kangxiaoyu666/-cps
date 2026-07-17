import axios from "axios";

export interface ApiResponse<T> { code: string; message: string; data: T; requestId: string }
export const http = axios.create({ baseURL: "/api/v1", timeout: 10000, withCredentials: true });
http.interceptors.response.use((response) => response.data, (error) => Promise.reject(new Error(error.response?.data?.message ?? "请求失败，请稍后重试")));
