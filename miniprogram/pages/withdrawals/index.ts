import { Withdrawal, WithdrawalRequest } from "../../utils/api-types";
import { request } from "../../utils/request";

function amountCent(value: string): number | null {
  const normalized = value.trim();
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(normalized)) return null;
  const [yuan, fraction = ""] = normalized.split(".");
  const cents = Number(yuan) * 100 + Number(fraction.padEnd(2, "0"));
  return Number.isSafeInteger(cents) && cents > 0 ? cents : null;
}

function idempotencyKey(): string {
  return `wd-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

Page({
  data: {
    loading: true,
    errorMessage: "",
    amountYuan: "",
    submitting: false,
    submitKey: "",
    cancelingId: 0,
    items: [] as Withdrawal[]
  },
  async onLoad() { await this.load(); },
  async onPullDownRefresh() {
    await this.load();
    wx.stopPullDownRefresh();
  },
  onAmountInput(event: WechatMiniprogram.Input) {
    this.setData({ amountYuan: event.detail.value, submitKey: "", errorMessage: "" });
  },
  async load() {
    this.setData({ loading: true, errorMessage: "" });
    try {
      const items = await request<Withdrawal[]>("/mini/withdrawals");
      this.setData({ items: Array.isArray(items) ? items : [] });
    } catch (error) {
      this.setData({ errorMessage: error instanceof Error ? error.message : "加载失败，请稍后重试" });
    } finally {
      this.setData({ loading: false });
    }
  },
  async submit() {
    if (this.data.submitting || this.data.cancelingId) return;
    const cents = amountCent(this.data.amountYuan);
    if (cents === null) {
      this.setData({ errorMessage: "请输入最多两位小数的有效金额" });
      return;
    }
    const submitKey = this.data.submitKey || idempotencyKey();
    this.setData({ submitting: true, submitKey, errorMessage: "" });
    try {
      await request("/mini/withdrawals", {
        method: "POST",
        header: { "Idempotency-Key": submitKey },
        data: { amountCent: cents } as WithdrawalRequest
      });
      this.setData({ amountYuan: "", submitKey: "" });
      wx.showToast({ title: "提现申请已提交", icon: "success" });
      await this.load();
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "提交失败，请稍后重试";
      this.setData({ errorMessage });
      wx.showToast({ title: errorMessage, icon: "none" });
    } finally {
      this.setData({ submitting: false });
    }
  },
  async cancel(event: WechatMiniprogram.TouchEvent) {
    const id = Number(event.currentTarget.dataset.id);
    if (!Number.isSafeInteger(id) || id <= 0 || this.data.submitting || this.data.cancelingId) return;
    const confirmed = await new Promise<boolean>((resolve) => wx.showModal({
      title: "取消提现",
      content: "取消后冻结金额将退回可用余额，确认取消吗？",
      success: ({ confirm }) => resolve(confirm),
      fail: () => resolve(false)
    }));
    if (!confirmed) return;
    this.setData({ cancelingId: id, errorMessage: "" });
    try {
      await request(`/mini/withdrawals/${id}/cancel`, { method: "POST" });
      wx.showToast({ title: "已取消", icon: "success" });
      await this.load();
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "取消失败，请稍后重试";
      this.setData({ errorMessage });
      wx.showToast({ title: errorMessage, icon: "none" });
    } finally {
      this.setData({ cancelingId: 0 });
    }
  }
});
