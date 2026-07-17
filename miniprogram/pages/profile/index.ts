import { request } from "../../utils/request";

interface Profile {
  id: number;
  nickname: string | null;
  avatar_url: string | null;
  invite_code: string;
  status: string;
}

Page({
  data: {
    loading: true,
    errorMessage: "",
    profile: null as Profile | null
  },
  async onShow() {
    await this.load();
  },
  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },
  async load() {
    this.setData({ loading: true, errorMessage: "" });
    try {
      this.setData({ profile: await request<Profile>("/mini/profile") });
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "账户加载失败" });
    } finally {
      this.setData({ loading: false });
    }
  }
});
