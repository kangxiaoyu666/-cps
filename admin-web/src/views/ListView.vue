<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import client, { getPage } from "../api/request";

const props = defineProps<{ section: string; title: string }>();
const loading = ref(true);
const error = ref("");
const rows = ref<Record<string, unknown>[]>([]);
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const dialog = ref<"content" | "pid" | "rule" | "reject" | "paid" | "">("");
const operatingId = ref(0);
const submitting = ref(false);
const form = reactive({
  configType: "BANNER",
  configKey: "home-main",
  contentText: "{\n  \"title\": \"首页活动\"\n}",
  platform: "MEITUAN",
  externalPid: "",
  externalSid: "",
  relationId: "",
  selfRatePercent: 50,
  directRatePercent: 10,
  reason: "",
  channel: "WECHAT",
  reference: "",
  proofUrl: "",
});

const columns = computed(() => {
  const keys = new Set<string>();
  rows.value.forEach((row) => Object.keys(row).forEach((key) => keys.add(key)));
  return [...keys];
});
const supportsCreate = computed(() => ["content", "pids", "commission-rules"].includes(props.section));
const supportsRowAction = computed(() => ["orders", "withdrawals"].includes(props.section));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const result = await getPage(props.section, { page: page.value, pageSize: pageSize.value });
    rows.value = result.items;
    total.value = result.total;
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "数据加载失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (props.section === "content") dialog.value = "content";
  if (props.section === "pids") dialog.value = "pid";
  if (props.section === "commission-rules") dialog.value = "rule";
}

async function submitCreate() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    if (dialog.value === "content") {
      const content = JSON.parse(form.contentText) as Record<string, unknown>;
      await client.put(`/content/${encodeURIComponent(form.configType)}/${encodeURIComponent(form.configKey)}`, {
        status: "PUBLISHED",
        content,
      });
      ElMessage.success("内容已发布");
    } else if (dialog.value === "pid") {
      await client.post("/pids", {
        platform: form.platform,
        externalPid: form.externalPid,
        externalSid: form.externalSid || null,
        relationId: form.relationId || null,
      });
      ElMessage.success("推广位已新增");
    } else if (dialog.value === "rule") {
      await client.post("/commission-rules", {
        selfRateBps: Math.round(form.selfRatePercent * 100),
        directInviteRateBps: Math.round(form.directRatePercent * 100),
      });
      ElMessage.success("分佣规则已生效");
    }
    dialog.value = "";
    await load();
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}

async function retryOrder(row: Record<string, unknown>) {
  const id = Number(row.id);
  if (!id || operatingId.value) return;
  operatingId.value = id;
  try {
    await client.post(`/orders/${id}/retry`);
    ElMessage.success("订单已重新查询并处理");
    await load();
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : "重试失败");
  } finally {
    operatingId.value = 0;
  }
}

async function approveWithdrawal(row: Record<string, unknown>) {
  const id = Number(row.id);
  await ElMessageBox.confirm("确认该提现资料已核验，可以进入付款阶段？", "审核提现", { type: "warning" });
  operatingId.value = id;
  try {
    await client.post(`/withdrawals/${id}/approve`);
    ElMessage.success("提现已审核通过");
    await load();
  } finally {
    operatingId.value = 0;
  }
}

function openWithdrawalDialog(type: "reject" | "paid", row: Record<string, unknown>) {
  operatingId.value = Number(row.id);
  form.reason = "";
  form.reference = "";
  form.proofUrl = "";
  dialog.value = type;
}

async function submitWithdrawal() {
  if (submitting.value || !operatingId.value) return;
  submitting.value = true;
  try {
    if (dialog.value === "reject") {
      await client.post(`/withdrawals/${operatingId.value}/reject`, { reason: form.reason });
      ElMessage.success("提现已拒绝并释放冻结余额");
    } else {
      await client.post(`/withdrawals/${operatingId.value}/paid`, {
        channel: form.channel,
        reference: form.reference,
        proofUrl: form.proofUrl,
      });
      ElMessage.success("付款信息已登记");
    }
    dialog.value = "";
    operatingId.value = 0;
    await load();
  } catch (reason) {
    ElMessage.error(reason instanceof Error ? reason.message : "操作失败");
  } finally {
    submitting.value = false;
  }
}

function closeDialog() {
  dialog.value = "";
  operatingId.value = 0;
}
function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}
function changePage(nextPage: number) { page.value = nextPage; void load(); }
function changePageSize(nextPageSize: number) { pageSize.value = nextPageSize; page.value = 1; void load(); }
watch(() => props.section, () => { page.value = 1; void load(); });
onMounted(load);
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <div><h3>{{ title }}</h3><span class="hint">全部结果均限制在当前登录租户</span></div>
      <div>
        <el-button
          v-if="supportsCreate"
          type="primary"
          @click="openCreate"
        >
          新增
        </el-button>
        <el-button
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
      </div>
    </div>
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
    <el-empty
      v-else-if="rows.length === 0"
      description="暂无数据"
    />
    <template v-else>
      <el-table :data="rows">
        <el-table-column
          v-for="column in columns"
          :key="column"
          :label="column"
          :prop="column"
          min-width="150"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ formatCell(scope.row[column]) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="supportsRowAction"
          label="操作"
          fixed="right"
          min-width="230"
        >
          <template #default="scope">
            <el-button
              v-if="section === 'orders'"
              size="small"
              :loading="operatingId === Number(scope.row.id)"
              @click="retryOrder(scope.row)"
            >
              单笔重试
            </el-button>
            <template v-if="section === 'withdrawals'">
              <el-button
                v-if="scope.row.status === 'SUBMITTED'"
                size="small"
                type="primary"
                @click="approveWithdrawal(scope.row)"
              >
                通过
              </el-button>
              <el-button
                v-if="scope.row.status === 'SUBMITTED'"
                size="small"
                type="danger"
                plain
                @click="openWithdrawalDialog('reject', scope.row)"
              >
                拒绝
              </el-button>
              <el-button
                v-if="scope.row.status === 'APPROVED'"
                size="small"
                type="success"
                @click="openWithdrawalDialog('paid', scope.row)"
              >
                登记付款
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        style="margin-top:20px;justify-content:flex-end"
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </template>

    <el-dialog
      :model-value="Boolean(dialog)"
      :title="dialog === 'content' ? '发布内容' : dialog === 'pid' ? '新增推广位' : dialog === 'rule' ? '新建分佣规则' : dialog === 'reject' ? '拒绝提现' : '登记线下付款'"
      width="560px"
      @close="closeDialog"
    >
      <el-form label-position="top">
        <template v-if="dialog === 'content'">
          <el-form-item label="内容类型">
            <el-input v-model="form.configType" />
          </el-form-item>
          <el-form-item label="内容键">
            <el-input v-model="form.configKey" />
          </el-form-item>
          <el-form-item label="内容 JSON">
            <el-input
              v-model="form.contentText"
              type="textarea"
              :rows="8"
            />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'pid'">
          <el-form-item label="平台">
            <el-select v-model="form.platform">
              <el-option
                label="美团"
                value="MEITUAN"
              /><el-option
                label="饿了么"
                value="ELEME"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="PID">
            <el-input v-model="form.externalPid" />
          </el-form-item>
          <el-form-item label="SID（可选）">
            <el-input v-model="form.externalSid" />
          </el-form-item>
          <el-form-item label="关系 ID（可选）">
            <el-input v-model="form.relationId" />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'rule'">
          <el-form-item label="自购奖励比例（%）">
            <el-input-number
              v-model="form.selfRatePercent"
              :min="0"
              :max="100"
            />
          </el-form-item>
          <el-form-item label="一级邀请奖励比例（%）">
            <el-input-number
              v-model="form.directRatePercent"
              :min="0"
              :max="100"
            />
          </el-form-item>
          <el-alert
            title="两项合计不能超过 100%，新规则生效时旧规则自动失效。"
            type="info"
            :closable="false"
          />
        </template>
        <template v-else-if="dialog === 'reject'">
          <el-form-item label="拒绝原因">
            <el-input
              v-model="form.reason"
              type="textarea"
              :rows="4"
            />
          </el-form-item>
        </template>
        <template v-else-if="dialog === 'paid'">
          <el-form-item label="付款渠道">
            <el-select v-model="form.channel">
              <el-option
                label="微信"
                value="WECHAT"
              /><el-option
                label="支付宝"
                value="ALIPAY"
              /><el-option
                label="银行卡"
                value="BANK"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="付款流水号">
            <el-input v-model="form.reference" />
          </el-form-item>
          <el-form-item label="付款凭证 URL">
            <el-input v-model="form.proofUrl" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">
          取消
        </el-button><el-button
          type="primary"
          :loading="submitting"
          @click="dialog === 'reject' || dialog === 'paid' ? submitWithdrawal() : submitCreate()"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
