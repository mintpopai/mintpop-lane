<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import {
  buildUserPayload,
  selectableFrontNodes,
  selectableLandNodes,
  userToForm,
  type UserFormModel,
} from "../utils/userForm";

const props = defineProps<{
  modelValue: boolean;
  user: AdminUserResponse;
  nodes: AdminNodeResponse[];
}>();
const emit = defineEmits<{ "update:modelValue": [boolean]; saved: [] }>();

const form = ref<UserFormModel>(userToForm(props.user));
const submitting = ref(false);

const 标题 = computed(() => `编辑用户：${props.user.name}`);
const 前置可选 = computed(() => selectableFrontNodes(props.nodes));
// 锚点用「库里那条记录原本占着的节点」而不是表单当前选中值：后者一旦被改动，
// 原节点就会从下拉里消失、再也切不回去，只能关掉抽屉丢弃全部改动重来
const 落地可选 = computed(() => selectableLandNodes(props.nodes, props.user.landNodeId));

watch(
  () => props.modelValue,
  (opened) => {
    if (opened) {
      form.value = userToForm(props.user);
    }
  },
);

async function 提交(): Promise<void> {
  submitting.value = true;
  try {
    const payload = buildUserPayload(form.value);
    await adminApi().updateUser(props.user.id, payload);
    ElMessage.success("已保存");
    emit("update:modelValue", false);
    emit("saved");
  } catch (error) {
    // 410002 落地节点被占，由服务端给中文提示
    ElMessage.error(error instanceof BizError ? error.message : `保存失败：${(error as Error).message}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="标题"
    size="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions :column="2" border style="margin-bottom: 16px">
      <el-descriptions-item label="邮箱">{{ user.email }}</el-descriptions-item>
      <el-descriptions-item label="姓名">{{ user.name }}</el-descriptions-item>
      <el-descriptions-item label="Logto id">{{ user.subject }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ USER_ROLE_LABELS[user.role] }}</el-descriptions-item>
    </el-descriptions>
    <p style="margin: 0 0 16px; color: #909399">姓名与邮箱随登录自动同步；授予管理员请直接改库。</p>

    <el-form label-width="110px">
      <el-form-item label="状态">
        <el-select v-model="form.status">
          <el-option v-for="(label, value) in USER_STATUS_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="第一跳节点">
        <el-select v-model="form.frontNodeId" clearable placeholder="可不分配">
          <el-option v-for="node in 前置可选" :key="node.id" :label="node.name" :value="node.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="落地节点">
        <el-select v-model="form.landNodeId" clearable placeholder="可暂不分配">
          <el-option
            v-for="node in 落地可选"
            :key="node.id"
            :label="`${node.name}（${node.egressIps.join('、') || '未填出口 IP'}）`"
            :value="node.id"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="提交()">保存</el-button>
    </template>
  </el-drawer>
</template>
