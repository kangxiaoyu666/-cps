type ApiResponse<T> = { code: string; message: string; data: T; requestId: string };
type RequestOptions = Omit<WechatMiniprogram.RequestOption, "url">;
let reloginPromise: Promise<void> | null = null;

function login(): Promise<void> {
  if (reloginPromise) return reloginPromise;
  const pending = new Promise<void>((resolve, reject) => wx.login({
    success: ({ code }) => {
      const scene = wx.getStorageSync("pendingShareScene") || undefined;
      wx.request<ApiResponse<{ token: string }>>({
        url: `${getApp<MiniApp>().globalData.apiBaseUrl}/mini/auth/login`,
        method: "POST",
        data: { code, scene },
        success: (res) => {
          if (res.data.code !== "SUCCESS") {
            reject(new Error(res.data.message));
            return;
          }
          wx.setStorageSync("sessionToken", res.data.data.token);
          wx.removeStorageSync("pendingShareScene");
          resolve();
        },
        fail: reject
      });
    },
    fail: reject
  }));
  reloginPromise = pending.finally(() => { reloginPromise = null; });
  return reloginPromise;
}

export async function request<T>(path: string, options: RequestOptions = {}, retried = false): Promise<T> {
  const token = wx.getStorageSync("sessionToken");
  return new Promise((resolve, reject) => wx.request<ApiResponse<T>>({
    ...options,
    url: `${getApp<MiniApp>().globalData.apiBaseUrl}${path}`,
    header: {
      "Content-Type": "application/json",
      ...(options.header || {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    success: async (res) => {
      if (res.statusCode === 401 && !retried) {
        try {
          await login();
          resolve(await request<T>(path, options, true));
        } catch (error) {
          reject(error);
        }
        return;
      }
      if (res.data.code !== "SUCCESS") {
        reject(new Error(res.data.message));
        return;
      }
      resolve(res.data.data);
    },
    fail: reject
  }));
}

