import { createRouter,createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import LoginView from "../views/LoginView.vue";
import AppLayout from "../layouts/AppLayout.vue";
import DashboardView from "../views/DashboardView.vue";
import ResourceView from "../views/ResourceView.vue";
const resources=[
  ["content","内容管理","content"],["channels","渠道管理","channels"],["pids","PID 管理","pids"],["users","用户管理","users"],["orders","订单管理","orders"],["commission-rules","佣金规则","commission-rules"],["wallets","钱包管理","wallets"],["withdrawals","提现管理","withdrawals"],["audit","审计日志","audit-logs"]
];
const router=createRouter({history:createWebHistory(),routes:[{path:"/login",component:LoginView,meta:{public:true}},{path:"/",component:AppLayout,redirect:"/dashboard",children:[{path:"dashboard",component:DashboardView,meta:{title:"仪表盘"}},...resources.map(([path,title,resource])=>({path,component:ResourceView,props:{title,resource},meta:{title}}))]}]});
router.beforeEach((to)=>{const auth=useAuthStore();if(!to.meta.public&&!auth.authenticated)return "/login";if(to.path==="/login"&&auth.authenticated)return "/dashboard";});
export default router;
