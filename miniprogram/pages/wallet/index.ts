import { request } from "../../utils/request";

interface WalletSummary {
  availableCent: number;
  frozenCent: number;
  debtCent: number;
  lifetimeIncomeCent: number;
}

interface WalletEntry {
  business_type: string;
  business_no: string;
  available_delta_cent: number;
  frozen_delta_cent: number;
  debt_delta_cent?: number;
  available_after_cent: number;
  frozen_after_cent: number;
  created_at: string;
}

Page({
  data: {
    loading: true,
    errorMessage: "",
    summary: { availableCent: 0, frozenCent: 0, debtCent: 0, lifetimeIncomeCent: 0 } as WalletSummary,
    entries: [] as WalletEntry[]
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
      const [summary, entries] = await Promise.all([
        request<WalletSummary>("/mini/wallet"),
        request<WalletEntry[]>("/mini/wallet/entries?page=1&pageSize=20")
      ]);
      this.setData({ summary, entries: Array.isArray(entries) ? entries : [] });
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "钱包加载失败" });
    } finally {
      this.setData({ loading: false });
    }
  }
});
