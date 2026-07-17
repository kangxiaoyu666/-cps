import { request } from "../../utils/request";

interface HelpItem {
  config_key: string;
  content_json: Record<string, unknown> | string;
}

function parseContent(value: HelpItem["content_json"]): Record<string, unknown> {
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
    items: [] as Array<{ key: string; title: string; content: string }>
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
      const rows = await request<HelpItem[]>("/mini/content/help");
      const items = rows.map((row) => {
        const content = parseContent(row.content_json);
        return {
          key: row.config_key,
          title: typeof content.title === "string" ? content.title : row.config_key,
          content: typeof content.content === "string" ? content.content : ""
        };
      });
      this.setData({ items, loading: false });
    } catch (_) {
      this.setData({ error: true, loading: false });
    }
  }
});
