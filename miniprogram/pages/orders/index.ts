import { request } from "../../utils/request";

interface AffiliateOrder {
  external_order_id: string;
  platform: string;
  status: string;
  order_amount_cent: number;
  settled_commission_cent: number;
  paid_at: string | null;
}

Page({
  data: {
    loading: true,
    errorMessage: "",
    items: [] as AffiliateOrder[]
  },
  async onLoad() {
    await this.load();
  },
  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },
  async load() {
    this.setData({ loading: true, errorMessage: "" });
    try {
      const items = await request<AffiliateOrder[]>("/mini/orders?page=1&pageSize=50");
      this.setData({ items: Array.isArray(items) ? items : [] });
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "订单加载失败" });
    } finally {
      this.setData({ loading: false });
    }
  }
});
