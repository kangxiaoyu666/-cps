import { getPromotionActivityCode } from "../../utils/config";
import { openPromotion } from "../../utils/promotion";

Page({
  data: { loading: false, errorMessage: "" },
  async claim() {
    if (this.data.loading) return;
    this.setData({ loading: true, errorMessage: "" });
    try {
      const result = await openPromotion("ELEME", getPromotionActivityCode("ELEME"));
      if (result === "copied") wx.showToast({ title: "链接已复制", icon: "success" });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "领取失败，请稍后重试";
      this.setData({ errorMessage });
      wx.showToast({ title: errorMessage, icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  }
});
