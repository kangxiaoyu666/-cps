<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import {
  deleteAffiliateConfiguration,
  listAffiliateConfigurations,
  saveAffiliateConfiguration,
  syncAffiliateOrders,
  validateAffiliateConfiguration,
  type AffiliatePlatform,
  type ConfigurationView,
  type ConfigurationWrite,
} from "../api/affiliateConfigurations";

interface ChannelForm {
  displayName: string;
  appKey: string;
  appSecret: string;
  activityId: string;
  pid: string;
  sid: string;
  permissions: string[];
  enabled: boolean;
}

interface PlatformDefinition {
  platform: AffiliatePlatform;
  name: string;
  description: string;
  color: string;
  permissions: Array<{ label: string; value: string; required?: boolean }>;
}

const platforms: PlatformDefinition[] = [
  {
    platform: "MEITUAN",
    name: "美团联盟",
    description: "配置美团联盟转链与订单查询能力",
    color: "#ffc300",
    permissions: [
      { label: "推广链接", value: "GET_REFERRAL_LINK", required: true },
      { label: "订单查询", value: "QUERY_ORDER", required: true },
    ],
  },
  {
    platform: "ELEME",
    name: "饿了么 / 淘宝闪购",
    description: "配置淘宝开放平台活动与订单能力",
    color: "#1677ff",
    permissions: [
      { label: "官方活动", value: "OFFICIAL_ACTIVITY", required: true },
      { label: "正向订单", value: "POSITIVE_ORDER", required: true },
      { label: "退款订单", value: "REFUND_ORDER" },
    ],
  },
];

const loading = ref(true);
const loadError = ref("");
const configurations = ref<ConfigurationView[]>([]);
const activePlatform = ref<AffiliatePlatform>("MEITUAN");
const formRefs = reactive<Partial<Record<AffiliatePlatform, FormInstance>>>({});
const busy = reactive<Record<AffiliatePlatform, string>>({ MEITUAN: "", ELEME: "" });
const forms = reactive<Record<AffiliatePlatform, ChannelForm>>({
  MEITUAN: emptyForm(platforms[0]),
  ELEME: emptyForm(platforms[1]),
});

function configuredCount(): number {
  return configurations.value.filter((item) => item.secretConfigured).length;
}

function emptyForm(definition: PlatformDefinition): ChannelForm {
  return {
    displayName: definition.name,
    appKey: "",
    appSecret: "",
    activityId: "",
    pid: "",
    sid: "",
    permissions: definition.permissions.filter((item) => item.required).map((item) => item.value),
    enabled: true,
  };
}

function configuration(platform: AffiliatePlatform): ConfigurationView | undefined {
  return configurations.value.find((item) => item.platform === platform);
}

function hydrate(platform: AffiliatePlatform, value?: ConfigurationView) {
  const definition = platforms.find((item) => item.platform === platform)!;
  Object.assign(forms[platform], value
    ? {
        displayName: value.displayName,
        appKey: "",
        appSecret: "",
        activityId: value.activityId ?? "",
        pid: value.pid ?? "",
        sid: value.sid ?? "",
        permissions: [...value.permissions],
        enabled: value.status === "ACTIVE",
      }
    : emptyForm(definition));
  formRefs[platform]?.clearValidate();
}

function setConfigurations(values: ConfigurationView[]) {
  configurations.value = values;
  for (const definition of platforms) hydrate(definition.platform, configuration(definition.platform));
}

async function load() {
  if (loading.value && configurations.value.length) return;
  loading.value = true;
  loadError.value = "";
  try {
    setConfigurations(await listAffiliateConfigurations());
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "渠道配置加载失败";
  } finally {
    loading.value = false;
  }
}

function rules(platform: AffiliatePlatform): FormRules<ChannelForm> {
  const existing = configuration(platform);
  return {
    displayName: [{ required: true, whitespace: true, message: "请输入渠道名称", trigger: "blur" }],
    appKey: existing?.secretConfigured
      ? []
      : [{ required: true, whitespace: true, message: "请输入 App Key", trigger: "blur" }],
    appSecret: existing?.secretConfigured
      ? []
      : [{ required: true, whitespace: true, message: "请输入 App Secret", trigger: "blur" }],
    activityId: [{ required: true, whitespace: true, message: "请输入活动 ID", trigger: "blur" }],
    pid: [{ required: true, whitespace: true, message: "请输入 PID", trigger: "blur" }],
    permissions: [{ type: "array", min: 1, message: "请至少选择一项 API 权限", trigger: "change" }],
  };
}

function payload(platform: AffiliatePlatform): ConfigurationWrite {
  const form = forms[platform];
  return {
    displayName: form.displayName.trim(),
    appKey: form.appKey.trim(),
    appSecret: form.appSecret,
    activityId: form.activityId.trim(),
    pid: form.pid.trim(),
    sid: form.sid.trim() || null,
    permissions: [...form.permissions],
    enabled: form.enabled,
  };
}

async function save(platform: AffiliatePlatform) {
  if (busy[platform]) return;
  const valid = await formRefs[platform]?.validate().catch(() => false);
  if (!valid) return;
  const existing = configuration(platform);
  if (existing?.status === "ACTIVE" && !forms[platform].enabled) {
    await ElMessageBox.confirm("停用后该渠道将无法转链或同步订单，确定继续保存吗？", "确认停用渠道", {
      type: "warning",
      confirmButtonText: "停用并保存",
      cancelButtonText: "取消",
    });
  }
  busy[platform] = "save";
  try {
    const saved = await saveAffiliateConfiguration(platform, payload(platform));
    configurations.value = [...configurations.value.filter((item) => item.platform !== platform), saved];
    hydrate(platform, saved);
    ElMessage.success(`${platformName(platform)}配置已保存`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "保存失败");
  } finally {
    forms[platform].appSecret = "";
    busy[platform] = "";
  }
}

async function validate(platform: AffiliatePlatform) {
  if (busy[platform]) return;
  busy[platform] = "validate";
  try {
    const result = await validateAffiliateConfiguration(platform);
    if (result.valid) {
      ElMessage.success(result.message);
    } else {
      ElMessage.error(`${result.message}（${result.code}）`);
    }
    const latest = await listAffiliateConfigurations();
    setConfigurations(latest);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "验证失败");
  } finally {
    busy[platform] = "";
  }
}

async function sync(platform: AffiliatePlatform) {
  if (busy[platform]) return;
  busy[platform] = "sync";
  try {
    const result = await syncAffiliateOrders(platform);
    const continuation = result.continuationRequired ? "，仍有数据将在下次继续" : "";
    ElMessage.success(`同步完成：扫描 ${result.scannedCount} 条，成功 ${result.successCount} 条${continuation}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "同步失败");
  } finally {
    busy[platform] = "";
  }
}

async function remove(platform: AffiliatePlatform) {
  if (busy[platform]) return;
  await ElMessageBox.confirm(
    "删除后将清除服务端保存的联盟凭据并停用渠道，此操作不可撤销。",
    `删除并停用${platformName(platform)}？`,
    { type: "warning", confirmButtonText: "删除并停用", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" },
  );
  busy[platform] = "delete";
  try {
    await deleteAffiliateConfiguration(platform);
    configurations.value = configurations.value.filter((item) => item.platform !== platform);
    hydrate(platform);
    ElMessage.success("渠道配置已删除并停用");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "删除失败");
  } finally {
    busy[platform] = "";
  }
}

function platformName(platform: AffiliatePlatform): string {
  return platforms.find((item) => item.platform === platform)!.name;
}

function formatTime(value: string | null): string {
  if (!value) return "尚未执行";
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function validationType(result: string | null): "success" | "danger" | "info" {
  if (result?.startsWith("SUCCESS:")) return "success";
  if (result?.startsWith("FAILED:")) return "danger";
  return "info";
}

function setFormRef(platform: AffiliatePlatform, instance: FormInstance | null) {
  if (instance) formRefs[platform] = instance;
}

onMounted(load);
</script>

<template>
  <section class="channel-page">
    <div class="channel-heading">
      <div>
        <h3>联盟渠道配置</h3>
        <p class="hint">
          凭据仅提交到服务端加密保存，前端不持久化任何 Secret。
        </p>
      </div>
      <div class="channel-summary">
        <span>已配置 {{ configuredCount() }}/{{ platforms.length }}</span>
        <el-button
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      :title="loadError"
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
      :rows="8"
      animated
    />
    <template v-else>
      <el-empty
        v-if="configurations.length === 0"
        description="尚未配置联盟渠道，请选择平台填写正式凭据。"
      />

      <div
        class="platform-tabs"
        role="tablist"
        aria-label="联盟平台"
      >
        <button
          v-for="item in platforms"
          :key="item.platform"
          type="button"
          class="platform-tab"
          :class="{ active: activePlatform === item.platform }"
          :aria-selected="activePlatform === item.platform"
          @click="activePlatform = item.platform"
        >
          <span
            class="platform-mark"
            :style="{ backgroundColor: item.color }"
          >{{ item.name.slice(0, 1) }}</span>
          <span><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span>
          <el-tag
            v-if="configuration(item.platform)?.secretConfigured"
            :type="configuration(item.platform)?.status === 'ACTIVE' ? 'success' : 'info'"
          >
            {{ configuration(item.platform)?.status === "ACTIVE" ? "已启用" : "已停用" }}
          </el-tag>
          <el-tag
            v-else
            type="warning"
          >
            未配置
          </el-tag>
        </button>
      </div>

      <article
        v-for="item in platforms"
        v-show="activePlatform === item.platform"
        :key="item.platform"
        class="channel-card"
      >
        <div class="channel-card-header">
          <div>
            <h4>{{ item.name }}</h4>
            <span class="hint">{{ item.description }}</span>
          </div>
          <div
            v-if="configuration(item.platform)"
            class="status-group"
          >
            <el-tag :type="configuration(item.platform)?.secretConfigured ? 'success' : 'info'">
              {{ configuration(item.platform)?.secretConfigured ? "Secret 已加密配置" : "Secret 未配置" }}
            </el-tag>
            <el-tag :type="validationType(configuration(item.platform)?.lastValidationResult ?? null)">
              {{ configuration(item.platform)?.lastValidationResult || "尚未验证" }}
            </el-tag>
          </div>
        </div>

        <el-form
          :ref="(instance: unknown) => setFormRef(item.platform, instance as FormInstance | null)"
          :model="forms[item.platform]"
          :rules="rules(item.platform)"
          label-position="top"
          :disabled="Boolean(busy[item.platform])"
        >
          <div class="form-grid">
            <el-form-item
              label="渠道名称"
              prop="displayName"
            >
              <el-input
                v-model="forms[item.platform].displayName"
                maxlength="80"
              />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch
                v-model="forms[item.platform].enabled"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
            <el-form-item
              label="App Key"
              prop="appKey"
            >
              <el-input
                v-model="forms[item.platform].appKey"
                autocomplete="off"
                :placeholder="configuration(item.platform)?.appKeyMasked ? `已配置 ${configuration(item.platform)?.appKeyMasked}，留空保持不变` : '请输入 App Key'"
              />
            </el-form-item>
            <el-form-item
              label="App Secret"
              prop="appSecret"
            >
              <el-input
                v-model="forms[item.platform].appSecret"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="configuration(item.platform)?.secretConfigured ? '已加密配置，留空保持不变' : '请输入 App Secret'"
              />
            </el-form-item>
            <el-form-item
              label="活动 ID"
              prop="activityId"
            >
              <el-input v-model="forms[item.platform].activityId" />
            </el-form-item>
            <el-form-item
              label="PID"
              prop="pid"
            >
              <el-input v-model="forms[item.platform].pid" />
            </el-form-item>
            <el-form-item
              label="SID（可选）"
              prop="sid"
            >
              <el-input
                v-model="forms[item.platform].sid"
                placeholder="用于自定义追踪标识"
              />
            </el-form-item>
            <el-form-item
              label="API 权限"
              prop="permissions"
              class="permission-field"
            >
              <el-checkbox-group v-model="forms[item.platform].permissions">
                <el-checkbox
                  v-for="permission in item.permissions"
                  :key="permission.value"
                  :value="permission.value"
                >
                  {{ permission.label }}<small>{{ permission.value }}</small>
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </div>
        </el-form>

        <div
          v-if="configuration(item.platform)"
          class="configuration-meta"
        >
          <span>最近验证：{{ formatTime(configuration(item.platform)?.lastValidatedAt ?? null) }}</span>
          <span>最近更新：{{ formatTime(configuration(item.platform)?.updatedAt ?? null) }}</span>
          <span>版本：{{ configuration(item.platform)?.version }}</span>
        </div>
        <div class="channel-actions">
          <el-button
            type="primary"
            :loading="busy[item.platform] === 'save'"
            :disabled="Boolean(busy[item.platform])"
            @click="save(item.platform)"
          >
            保存配置
          </el-button>
          <el-button
            :loading="busy[item.platform] === 'validate'"
            :disabled="Boolean(busy[item.platform]) || configuration(item.platform)?.status !== 'ACTIVE'"
            @click="validate(item.platform)"
          >
            验证连接
          </el-button>
          <el-button
            :loading="busy[item.platform] === 'sync'"
            :disabled="Boolean(busy[item.platform]) || configuration(item.platform)?.status !== 'ACTIVE'"
            @click="sync(item.platform)"
          >
            手动同步
          </el-button>
          <el-button
            v-if="configuration(item.platform)?.secretConfigured"
            type="danger"
            plain
            :loading="busy[item.platform] === 'delete'"
            :disabled="Boolean(busy[item.platform])"
            @click="remove(item.platform)"
          >
            删除并停用
          </el-button>
        </div>
      </article>
    </template>
  </section>
</template>
