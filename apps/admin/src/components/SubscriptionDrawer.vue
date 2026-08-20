<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { AGENT_TYPE_LABELS } from "../api/types";
import type { AdminSubscriptionResponse, AdminUserResponse } from "../api/types";
import { formatDateTime } from "../utils/format";
import {
  buildSubscriptionPayload,
  emptySubscriptionForm,
  subscriptionToForm,
  validateSubscriptionForm,
  type SubscriptionFormModel,
} from "../utils/subscriptionForm";

const props = defineProps<{ modelValue: boolean; user: AdminUserResponse }>();
const emit = defineEmits<{ "update:modelValue": [boolean]; changed: [] }>();

const 列表 = ref<AdminSubscriptionResponse[]>([]);
const loading = ref(false);
const 表单模式 = ref<"hidden" | "create" | "edit">("hidden");
const form = ref<SubscriptionFormModel>(emptySubscriptionForm());
const submitting = ref(false);

const 标题 = computed(() => `订阅管理：${props.user.name}`);
/** 管理员当前浏览器时区，标在表单里免得填的人心里没数 */
const 本地时区 = Intl.DateTimeFormat().resolvedOptions().timeZone;
// 编辑的这条如果 agentType 不在已知枚举里（服务端新增了本前端还不认识的类型），
// 下拉必须额外补一项原值，否则选项列表里找不到当前值，会被 el-select 悄悄清空
const agent选项 = computed<Array<{ value: string; label: string }>>(() => {
  const 已知 = Object.entries(AGENT_TYPE_LABELS).map(([value, label]) => ({ value, label }));
  if (form.value.agentType && !(form.value.agentType in AGENT_TYPE_LABELS)) {
    return [...已知, { value: form.value.agentType, label: `${form.value.agentType}（未知类型）` }];
  }
  return 已知;
});

function 报错(error: unknown, 前缀: string): void {
  ElMessage.error(error instanceof BizError ? error.message : `${前缀}：${(error as Error).message}`);
}

async function 加载列表(): Promise<void> {
  loading.value = true;
  try {
    列表.value = await adminApi().listSubscriptions(props.user.id);
  } catch (error) {
    报错(error, "加载订阅失败");
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.modelValue,
  (opened) => {
    if (opened) {
      表单模式.value = "hidden";
      void 加载列表();
    }
  },
  // 组件由 v-if 挂载，父组件常常「先设 user 再设 modelValue=true」在同一同步函数里完成，
  // 首次挂载时 props.modelValue 从创建起就已经是 true，不会再经历一次 false→true 的转换，
  // 不加 immediate 首次打开就看不到数据；回调内的 if (opened) 挡住了 modelValue 为 false 时的误触发
  { immediate: true },
);

function 新建(): void {
  form.value = emptySubscriptionForm();
  表单模式.value = "create";
}

function 编辑(subscription: AdminSubscriptionResponse): void {
  form.value = subscriptionToForm(subscription);
  表单模式.value = "edit";
}

function 收起表单(): void {
  表单模式.value = "hidden";
}

async function 提交(): Promise<void> {
  const errors = validateSubscriptionForm(form.value);
  if (errors.length > 0) {
    ElMessage.error(errors[0]);
    return;
  }

  submitting.value = true;
  try {
    const payload = buildSubscriptionPayload(form.value);
    if (表单模式.value === "edit") {
      await adminApi().updateSubscription(form.value.id as number, payload);
    } else {
      await adminApi().createSubscription(props.user.id, payload);
    }
    ElMessage.success("已保存");
    表单模式.value = "hidden";
    await 加载列表();
    emit("changed");
  } catch (error) {
    报错(error, "保存失败");
  } finally {
    submitting.value = false;
  }
}

async function 删除(subscription: AdminSubscriptionResponse): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除订阅「${subscription.name}」？`, "删除确认", { type: "warning" });
  } catch {
    return;
  }
  try {
    await adminApi().deleteSubscription(subscription.id);
    ElMessage.success("已删除");
    await 加载列表();
    emit("changed");
  } catch (error) {
    报错(error, "删除失败");
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="标题"
    size="640px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div style="margin-bottom: 12px">
      <el-button type="primary" @click="新建()">新增订阅</el-button>
    </div>

    <el-table v-loading="loading" :data="列表" border>
      <el-table-column prop="name" label="套餐名" min-width="140" show-overflow-tooltip />
      <el-table-column label="Agent" width="110">
        <template #default="{ row }">
          {{ AGENT_TYPE_LABELS[row.agentType as keyof typeof AGENT_TYPE_LABELS] ?? row.agentType }}
        </template>
      </el-table-column>
      <el-table-column label="起期" width="150">
        <template #default="{ row }">{{ formatDateTime(row.startsAt) }}</template>
      </el-table-column>
      <el-table-column label="止期" width="150">
        <template #default="{ row }">{{ formatDateTime(row.endsAt) }}</template>
      </el-table-column>
      <el-table-column label="凭据" width="90">
        <template #default="{ row }">
          <el-tag :type="row.hasCredential ? 'success' : 'info'">
            {{ row.hasCredential ? "已录入" : "未录入" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="编辑(row)">编辑</el-button>
          <el-button link type="danger" @click="删除(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider v-if="表单模式 !== 'hidden'" />

    <el-form v-if="表单模式 !== 'hidden'" label-width="110px">
      <el-form-item label="Agent 类型">
        <el-select v-model="form.agentType">
          <el-option v-for="opt in agent选项" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="套餐名">
        <el-input v-model="form.name" maxlength="64" show-word-limit />
      </el-form-item>
      <el-form-item label="起期">
        <el-date-picker
          v-model="form.startsAt"
          type="datetime"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="止期">
        <el-date-picker
          v-model="form.endsAt"
          type="datetime"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item>
        <span style="color: var(--el-text-color-secondary); font-size: 12px">
          起止期按你的本地时区（{{ 本地时区 }}）填写，保存为绝对时刻，各端按各自时区显示
        </span>
      </el-form-item>
      <el-form-item label="席位凭据">
        <el-input
          v-model="form.credential"
          type="password"
          show-password
          :placeholder="表单模式 === 'edit' ? '留空表示沿用原凭据' : ''"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" maxlength="255" show-word-limit />
      </el-form-item>
      <el-form-item>
        <el-button @click="收起表单()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="提交()">保存</el-button>
      </el-form-item>
    </el-form>
  </el-drawer>
</template>
