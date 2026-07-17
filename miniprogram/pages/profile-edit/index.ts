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
    saving: false,
    errorMessage: "",
    nickname: "",
    avatarUrl: ""
  },
  async onLoad() {
    await this.load();
  },
  async load() {
    this.setData({ loading: true, errorMessage: "" });
    try {
      const profile = await request<Profile>("/mini/profile");
      this.setData({ nickname: profile.nickname || "", avatarUrl: profile.avatar_url || "" });
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "资料加载失败" });
    } finally {
      this.setData({ loading: false });
    }
  },
  onNicknameInput(event: WechatMiniprogram.Input) {
    this.setData({ nickname: event.detail.value, errorMessage: "" });
  },
  onAvatarInput(event: WechatMiniprogram.Input) {
    this.setData({ avatarUrl: event.detail.value, errorMessage: "" });
  },
  async save() {
    if (this.data.saving) return;
    const nickname = this.data.nickname.trim();
    if (!nickname || nickname.length > 128) {
      this.setData({ errorMessage: "昵称长度应为 1 到 128 个字符" });
      return;
    }
    this.setData({ saving: true, errorMessage: "" });
    try {
      await request("/mini/profile", {
        method: "PUT",
        data: { nickname, avatarUrl: this.data.avatarUrl.trim() || null }
      });
      wx.showToast({ title: "资料已保存", icon: "success" });
      setTimeout(() => wx.navigateBack(), 400);
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "保存失败" });
    } finally {
      this.setData({ saving: false });
    }
  }
});
