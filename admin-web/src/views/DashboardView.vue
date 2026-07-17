<script setup lang="ts">
import { onMounted, ref } from "vue";
import { getDashboard, type DashboardSummary } from "../api/request";

const loading = ref(true);
const error = ref("");
const summary = ref<DashboardSummary | null>(null);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    summary.value = await getDashboard();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "运营数据加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <el-alert
    v-if="error"
    :title="error"
    type="error"
    show-icon
    :closable="false"
  >
    <template #default>
      <el-button
        link
        type="primary"
        @click="load"
      >
        重新加载
      </el-button>
    </template>
  </el-alert>
  <el-skeleton
    v-else-if="loading"
    :rows="6"
    animated
  />
  <template v-else-if="summary">
    <div class="metric-grid">
      <div class="metric">
        <small>今日订单</small><strong>{{ summary.ordersToday }}</strong><span>当前租户实时数据</span>
      </div>
      <div class="metric">
        <small>待审核提现</small><strong>{{ summary.pendingWithdrawals }}</strong><span>状态为 SUBMITTED</span>
      </div>
      <div class="metric">
        <small>租户 ID</small><strong>{{ summary.tenantId }}</strong><span>由服务端登录身份确定</span>
      </div>
    </div>
    <section class="panel">
      <div class="toolbar">
        <div><h3>运营概览</h3><span class="hint">数据来自当前租户的实际后台接口</span></div>
        <el-button @click="load">
          刷新
        </el-button>
      </div>
      <el-empty description="更多运营指标请从左侧对应业务模块查看。" />
    </section>
  </template>
</template>
