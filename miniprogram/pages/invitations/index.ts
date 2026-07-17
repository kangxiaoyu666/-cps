import { createShareScene, sharePath } from "../../utils/share";
import { request } from "../../utils/request";

interface Invitation {
  nickname: string;
  avatar_url: string | null;
  invited_at: string;
}

Page({
  data: {
    loading: true,
    errorMessage: "",
    items: [] as Invitation[],
    shareScene: ""
  },
  async onLoad() {
    await Promise.all([this.load(), this.prepareShareScene()]);
  },
  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },
  async load() {
    this.setData({ loading: true, errorMessage: "" });
    try {
      const items = await request<Invitation[]>("/mini/invitations");
      this.setData({ items: Array.isArray(items) ? items : [] });
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "加载失败，请稍后重试" });
    } finally {
      this.setData({ loading: false });
    }
  },
  async prepareShareScene(): Promise<string> {
    if (this.data.shareScene) return this.data.shareScene;
    try {
      const { scene } = await createShareScene();
      this.setData({ shareScene: scene });
      return scene;
    } catch (_) {
      return "";
    }
  },
  onShareAppMessage() {
    return {
      title: "一起领外卖红包",
      promise: this.prepareShareScene().then((scene: string) => ({
        title: "一起领外卖红包",
        path: scene ? sharePath("/pages/home/index", scene) : "/pages/home/index"
      }))
    };
  },
  onShareTimeline() {
    const scene = this.data.shareScene;
    if (!scene) void this.prepareShareScene();
    return {
      title: "一起领外卖红包",
      query: scene ? `scene=${encodeURIComponent(scene)}` : ""
    };
  }
});
