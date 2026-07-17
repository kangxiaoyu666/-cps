<script setup lang="ts">
import { useRoute,useRouter } from "vue-router";import { useAuthStore } from "../stores/auth";
const route=useRoute();const router=useRouter();const auth=useAuthStore();
const menus=[["/dashboard","仪表盘"],["/content","内容"],["/channels","渠道"],["/pids","PID"],["/users","用户"],["/orders","订单"],["/commission-rules","佣金规则"],["/wallets","钱包"],["/withdrawals","提现"],["/audit","审计"]];
function logout(){auth.logout();router.push("/login");}
</script>
<template>
  <el-container class="shell">
    <el-aside
      width="220px"
      class="aside"
    >
      <div class="brand">
        外卖 CPS 管理台
      </div><el-menu
        router
        :default-active="route.path"
      >
        <el-menu-item
          v-for="m in menus"
          :key="m[0]"
          :index="m[0]"
        >
          {{ m[1] }}
        </el-menu-item>
      </el-menu>
    </el-aside><el-container>
      <el-header class="header">
        <strong>{{ route.meta.title }}</strong><el-button
          text
          @click="logout"
        >
          退出登录
        </el-button>
      </el-header><el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
