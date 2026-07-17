import { AffiliatePlatform, PromotionLink, PromotionLinkRequest } from "./api-types";
import { request } from "./request";

const MINI_PROGRAM_URL = /^weixin:\/\/dl\/business\//i;
const APP_ID_URL = /[?&]appId=([^&#]+)(?:.*[?&]path=([^&#]+))?/i;
const promotionRequests = new Map<string, Promise<PromotionLink>>();

function decode(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch (_) {
    return value;
  }
}

function miniProgramTarget(url: string): { appId: string; path?: string } | null {
  const appIdMatch = url.match(APP_ID_URL);
  if (appIdMatch) {
    return { appId: decode(appIdMatch[1]), path: appIdMatch[2] ? decode(appIdMatch[2]) : undefined };
  }
  return MINI_PROGRAM_URL.test(url) ? { appId: "", path: url } : null;
}

function navigate(target: { appId: string; path?: string }): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!target.appId) {
      reject(new Error("推广链接未包含可跳转的小程序 appId"));
      return;
    }
    wx.navigateToMiniProgram({
      appId: target.appId,
      path: target.path,
      success: () => resolve(),
      fail: reject
    });
  });
}

function copy(url: string): Promise<void> {
  return new Promise((resolve, reject) => wx.setClipboardData({ data: url, success: () => resolve(), fail: reject }));
}

export async function openPromotion(platform: AffiliatePlatform, activityCode: string): Promise<"navigated" | "copied"> {
  const key = `${platform}:${activityCode}`;
  let pending = promotionRequests.get(key);
  if (!pending) {
    pending = request<PromotionLink>("/mini/promotion-links", {
      method: "POST",
      data: { platform, activityCode } as PromotionLinkRequest
    }).finally(() => { promotionRequests.delete(key); });
    promotionRequests.set(key, pending);
  }
  const link = await pending;
  if (!link.url) throw new Error("推广链接为空");
  const target = miniProgramTarget(link.url);
  if (target?.appId) {
    await navigate(target);
    return "navigated";
  }
  await copy(link.url);
  return "copied";
}
