<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "./stores/session";

const route = useRoute(); const router = useRouter(); const session = useSessionStore();
const publicPage = computed(() => route.meta.public === true);
const menus = [
  ["/", "运营概览"], ["/content", "内容配置"], ["/channels", "联盟渠道"], ["/pids", "推广位管理"],
  ["/users", "用户与邀请"], ["/orders", "联盟订单"], ["/commissions", "佣金记录"], ["/commission-rules", "分佣规则"], ["/wallets", "钱包流水"],
  ["/withdrawals", "提现审核"], ["/audits", "审计日志"]
];
</script>
<template>
  <router-view v-if="publicPage" />
  <el-container
    v-else
    class="shell"
  >
    <el-aside
      width="228px"
      class="sidebar"
    >
      <div class="brand">
        <span class="brand-mark">省</span><div><strong>外卖省心领</strong><small>租户运营中心</small></div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        class="menu"
      >
        <el-menu-item
          v-for="item in menus"
          :key="item[0]"
          :index="item[0]"
        >
          {{ item[1] }}
        </el-menu-item>
      </el-menu>
      <div class="scope-note">
        数据范围<br><strong>{{ session.tenantName }}</strong>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div><small>当前页面</small><h2>{{ route.meta.title }}</h2></div><div class="account">
          <span class="status-dot" />{{ session.displayName }}<el-button
            text
            @click="session.logout().finally(() => router.push('/login'))"
          >
            退出
          </el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
