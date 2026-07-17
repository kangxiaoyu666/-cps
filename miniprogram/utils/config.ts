import { AffiliatePlatform } from "./api-types";

export type MiniProgramEnvironment = "develop" | "trial" | "release";

const API_BASE_URLS: Record<MiniProgramEnvironment, string> = {
  develop: "http://localhost:8080/api/v1",
  trial: "",
  release: ""
};

interface RuntimeExtConfig {
  apiBaseUrl?: string;
}

function runtimeApiBaseUrl(): string {
  const extConfig = wx.getExtConfigSync?.() as RuntimeExtConfig | undefined;
  return extConfig?.apiBaseUrl?.trim() ?? "";
}

const PROMOTION_ACTIVITY_CODES: Record<AffiliatePlatform, string> = {
  MEITUAN: "",
  ELEME: ""
};

export function getPromotionActivityCode(platform: AffiliatePlatform): string {
  return PROMOTION_ACTIVITY_CODES[platform].trim();
}

export function getApiBaseUrl(): string {
  const environment = wx.getAccountInfoSync().miniProgram.envVersion;
  const apiBaseUrl = runtimeApiBaseUrl() || API_BASE_URLS[environment];
  if (!apiBaseUrl) throw new Error(`未配置 ${environment} 环境的 API 地址`);
  if (environment === "release" && /localhost|127\.0\.0\.1/i.test(apiBaseUrl)) {
    throw new Error("正式环境 API 地址配置无效");
  }
  return apiBaseUrl;
}
