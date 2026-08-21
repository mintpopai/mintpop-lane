<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { USER_ROLE_LABELS, USER_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, AdminUserResponse } from "../api/types";
import { showToast } from "../toast";
import {
  buildUserPayload,
  selectableFrontNodes,
  selectableLandNodes,
  userToForm,
  type UserFormModel,
} from "../utils/userForm";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

const props = defineProps<{ user: AdminUserResponse; nodes: AdminNodeResponse[] }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即初始化表单，不需要 watch 重置
const emit = defineEmits<{ close: []; saved: [] }>();

const form = ref<UserFormModel>(userToForm(props.user));
const submitting = ref(false);

const statusOptions = Object.entries(USER_STATUS_LABELS).map(([value, label]) => ({ value, label }));
const frontOptions = computed(() => [
  { value: null, label: "不分配" },
  ...selectableFrontNodes(props.nodes).map((node) => ({ value: node.id, label: node.name })),
]);
// 锚点用「库里那条记录原本占着的节点」而不是表单当前选中值：后者一旦被改动，
// 原节点就会从下拉里消失、再也切不回去，只能关掉弹窗丢弃全部改动重来
const landOptions = computed(() => [
  { value: null, label: "暂不分配" },
  ...selectableLandNodes(props.nodes, props.user.landNodeId).map((node) => ({
    value: node.id,
    label: `${node.name}（${node.egressIps.join("、") || "未填出口 IP"}）`,
  })),
]);

async function submit(): Promise<void> {
  submitting.value = true;
  try {
    const payload = buildUserPayload(form.value);
    await adminApi().updateUser(props.user.id, payload);
    showToast("success", "已保存");
    emit("saved");
    emit("close");
  } catch (error) {
    // 410002 落地节点被占，由服务端给中文提示
    showToast("error", error instanceof BizError ? error.message : `保存失败：${(error as Error).message}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="`编辑用户：${user.name}`" @close="emit('close')">
    <!-- 身份事实只读展示：这些随登录自动同步，不在这里改 -->
    <dl class="facts">
      <div class="facts-item"><dt>邮箱</dt><dd class="fact">{{ user.email }}</dd></div>
      <div class="facts-item"><dt>姓名</dt><dd>{{ user.name }}</dd></div>
      <div class="facts-item"><dt>Logto id</dt><dd class="fact">{{ user.subject }}</dd></div>
      <div class="facts-item"><dt>角色</dt><dd>{{ USER_ROLE_LABELS[user.role] }}</dd></div>
    </dl>
    <p class="admin-note">姓名与邮箱随登录自动同步；授予管理员请直接改库。</p>

    <div class="admin-form form-block">
      <div class="admin-field">
        <label :for="`user-status-${user.id}`">状态</label>
        <Select :id="`user-status-${user.id}`" v-model="form.status" :options="statusOptions" aria-label="状态" />
      </div>
      <div class="admin-field">
        <label :for="`user-front-${user.id}`">第一跳节点</label>
        <Select :id="`user-front-${user.id}`" v-model="form.frontNodeId" :options="frontOptions" aria-label="第一跳节点" />
      </div>
      <div class="admin-field">
        <label :for="`user-land-${user.id}`">落地节点</label>
        <Select :id="`user-land-${user.id}`" v-model="form.landNodeId" :options="landOptions" aria-label="落地节点" />
      </div>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="submitting" @click="submit()">
        {{ submitting ? "保存中…" : "保存" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  padding: 16px;
  margin-bottom: 12px;
  background: var(--color-bg-cloud);
  border-radius: var(--radius-card);
}

.facts-item dt {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.facts-item dd {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-block {
  margin-top: 16px;
}
</style>
