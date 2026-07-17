import { request } from "../../utils/request";

interface ContentItem {
  config_type: string;
  config_key: string;
  content_json: Record<string, unknown> | string;
}

interface HomeResponse {
  content: ContentItem[];
}

function parseContent(value: ContentItem["content_json"]): Record<string, unknown> {
  if (typeof value !== "string") return value ?? {};
  try {
    return JSON.parse(value) as Record<string, unknown>;
  } catch (_) {
    return {};
  }
}

Page({
  data: {
    loading: true,
    error: false,
    title: "先领红包，再点外卖",
    subtitle: "奖励来自真实、已结算、未退款订单"
  },
  async onLoad() {
    await this.load();
  },
  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },
  async load() {
    this.setData({ loading: true, error: false });
    try {
      const data = await request<HomeResponse>("/mini/home");
      const configured = data.content
        .map((item) => parseContent(item.content_json))
        .find((item) => typeof item.title === "string" || typeof item.subtitle === "string");
      this.setData({
        title: typeof configured?.title === "string" ? configured.title : this.data.title,
        subtitle: typeof configured?.subtitle === "string" ? configured.subtitle : this.data.subtitle,
        loading: false
      });
    } catch (_) {
      this.setData({ error: true, loading: false });
    }
  }
});
