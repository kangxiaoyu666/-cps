import { createRouter, createWebHistory } from "vue-router";
import LoginView from "./views/LoginView.vue";

const sections: Record<string, { title: string; resource: string }> = {
  content: { title: "内容配置", resource: "content" },
  pids: { title: "推广位管理", resource: "pids" },
  users: { title: "用户与邀请关系", resource: "users" },
  orders: { title: "联盟订单", resource: "orders" },
  commissions: { title: "佣金记录", resource: "commissions" },
  "commission-rules": { title: "分佣规则", resource: "commission-rules" },
  wallets: { title: "钱包流水", resource: "wallets" },
  withdrawals: { title: "提现审核", resource: "withdrawals" },
  audits: { title: "审计日志", resource: "audit-logs" },
};
const routes = [
  { path: "/login", component: LoginView, meta: { public: true } },
  {
    path: "/",
    component: () => import("./views/DashboardView.vue"),
    meta: { title: "运营概览" },
  },
  {
    path: "/channels",
    component: () => import("./views/ChannelsView.vue"),
    meta: { title: "联盟渠道" },
  },
  ...Object.entries(sections).map(([path, section]) => ({
    path: `/${path}`,
    component: () => import("./views/ListView.vue"),
    props: { section: section.resource, title: section.title },
    meta: { title: section.title },
  })),
];
const router = createRouter({ history: createWebHistory(), routes });
router.beforeEach((to) => {
  const authenticated = sessionStorage.getItem("admin-authenticated") === "true";
  if (to.meta.public !== true && !authenticated) return "/login";
  if (to.path === "/login" && authenticated) return "/";
});
export default router;
