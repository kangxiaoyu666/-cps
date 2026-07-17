import { getApiBaseUrl } from "./utils/config";

App<MiniApp>({
  globalData: { apiBaseUrl: getApiBaseUrl() },
  onLaunch(options) {
    const scene = options.query?.scene;
    if (scene) wx.setStorageSync("pendingShareScene", scene);
  }
});
