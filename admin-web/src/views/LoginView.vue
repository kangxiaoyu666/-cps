<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ tenantCode: "", username: "", password: "" });

async function submit() {
  if (!form.tenantCode || !form.username || !form.password) {
    ElMessage.warning("请填写租户编码、账号和密码");
    return;
  }
  loading.value = true;
  try {
    await session.login(form.tenantCode, form.username, form.password);
    await router.push("/");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "登录失败");
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-intro">
      <span>WAIMAI CPS</span><h1>每一笔真实订单<br>都有清晰归因</h1><p>只保留自购与一级直接邀请奖励。租户隔离、资金流水、人工提现，全程可审计。</p>
    </section><section class="login-card panel">
      <h2>登录运营中心</h2><p class="hint">
        使用租户管理员账号登录，租户范围由服务端身份确定。
      </p>
      <el-form
        label-position="top"
        size="large"
        @submit.prevent="submit"
      >
        <el-form-item label="租户编码">
          <el-input
            v-model="form.tenantCode"
            autocomplete="organization"
          />
        </el-form-item>
        <el-form-item label="账号">
          <el-input
            v-model="form.username"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          type="primary"
          style="width:100%"
          :loading="loading"
          @click="submit"
        >
          安全登录
        </el-button>
      </el-form><p class="hint">
        生产环境启用 HttpOnly Cookie、CSRF 防护与登录限流。
      </p>
    </section>
  </div>
</template>
