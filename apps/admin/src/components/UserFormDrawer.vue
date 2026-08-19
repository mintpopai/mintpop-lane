<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import {
  buildUserPayload,
  emptyUserForm,
  selectableFrontNodes,
  selectableLandNodes,
  userToForm,
  validateUserForm,
  type UserFormModel,
} from "../utils/userForm";

const props = defineProps<{
  modelValue: boolean;
  editing: AdminUserResponse | null;
  nodes: AdminNodeResponse[];
}>();
const emit = defineEmits<{ "update:modelValue": [boolean]; saved: [] }>();

const form = ref<UserFormModel>(emptyUserForm());
const submitting = ref(false);

const 标题 = computed(() => (props.editing ? `编辑用户：${props.editing.name}` : "新建用户"));
const 前置可选 = computed(() => selectableFrontNodes(props.nodes));
// 锚点用「库里那条记录原本占着的节点」而不是表单当前选中值：后者一旦被改动，
// 原节点就会从下拉里消失、再也切不回去，只能关掉抽屉丢弃全部改动重来
const 落地可选 = computed(() => selectableLandNodes(props.nodes, props.editing?.landNodeId ?? null));
const 凭据提示 = computed(() =>
  props.editing?.credentialConfigured ? "已配置，留空表示不修改" : "尚未配置",
);

watch(
  () => props.modelValue,
  (opened) => {
    if (opened) {
      form.value = props.editing ? userToForm(props.editing) : emptyUserForm();
    }
  },
);

async function 提交(): Promise<void> {
  const errors = validateUserForm(form.value);
  if (errors.length > 0) {
    ElMessage.warning(errors[0]);
    return;
  }

  submitting.value = true;
  try {
    const payload = buildUserPayload(form.value);
    if (props.editing) {
      await adminApi().updateUser(props.editing.id, payload);
    } else {
      await adminApi().createUser(payload);
    }
    ElMessage.success("已保存");
    emit("update:modelValue", false);
    emit("saved");
  } catch (error) {
    // 410002 落地节点被占、410004 Logto 用户已存在，都由服务端给中文提示
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
    <el-form label-width="130px">
      <el-form-item label="Logto user id">
        <el-input v-model="form.subject" placeholder="JWT 里的 sub" />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="form.status">
          <el-option v-for="(label, value) in USER_STATUS_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="第一跳节点">
        <el-select v-model="form.frontNodeId" placeholder="必选">
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
      <el-form-item label="席位凭据">
        <div style="width: 100%">
          <p style="margin: 0 0 8px; color: #909399">{{ 凭据提示 }}。加密存储，服务端永不回传。</p>
          <el-input
            v-model="form.claudeCredential"
            type="password"
            show-password
            placeholder="claude setup-token 生成的长效凭据"
          />
        </div>
      </el-form-item>
      <el-form-item label="角色">
        <span style="color: #909399">
          {{ editing ? USER_ROLE_LABELS[editing.role] : "新建的用户一律是普通成员" }}——授予管理员请直接改库
        </span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="提交()">保存</el-button>
    </template>
  </el-drawer>
</template>
