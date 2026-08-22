<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_PROTOCOL, NODE_ROLE_LABELS, NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, NodeProtocol, NodeRole } from "../api/types";
import { showToast } from "../toast";
import {
  applyProtocol,
  buildNodePayload,
  emptyNodeForm,
  nodeToForm,
  syncEgressIpFromServerAddr,
  validateNodeForm,
  type NodeFormModel,
} from "../utils/nodeForm";
import KeyValueEditor from "./KeyValueEditor.vue";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

const props = defineProps<{ role: NodeRole; editing: AdminNodeResponse | null }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即初始化表单，不需要 watch 重置
const emit = defineEmits<{ close: []; saved: [] }>();

const form = ref<NodeFormModel>(props.editing ? nodeToForm(props.editing) : emptyNodeForm(props.role));
const submitting = ref(false);

// 落地节点地址填成 IP 字面量时预填出口 IP（单 IP VPS 出口就是它自己），手工改过的值不覆盖
watch(
  () => form.value.serverAddr,
  (_, previousServerAddr) => {
    form.value = syncEgressIpFromServerAddr(form.value, previousServerAddr);
  },
);

const title = computed(() => (props.editing ? `编辑节点：${props.editing.name}` : "新建节点"));
const roleOptions = Object.entries(NODE_ROLE_LABELS).map(([value, label]) => ({ value, label }));
// 订阅导入的节点：参数由「重新拉取」统一更新，表单只放行名称/状态/备注
const isSubscriptionNode = computed(() => props.editing?.protocol === "MIHOMO");
// MIHOMO 只能经订阅导入产生，协议下拉不提供
const protocolOptions = Object.values(NODE_PROTOCOL)
  .filter((value) => value !== "MIHOMO")
  .map((value) => ({ value, label: value }));
const statusOptions = Object.entries(NODE_STATUS_LABELS).map(([value, label]) => ({ value, label }));

const protocolChanged = computed(
  () => props.editing !== null && props.editing.protocol !== form.value.protocol,
);
const secretKeyHint = computed(() => {
  // 协议一变，旧密钥就不再适用；此时若还显示「留空表示不修改」，管理员会以为留空是安全的
  if (protocolChanged.value) {
    return "协议已变更，必须重新填写——留空会让服务端继续沿用旧协议的密钥";
  }
  return props.editing?.secretConfigured ? "已配置，留空表示不修改" : "尚未配置";
});

function switchProtocol(protocol: NodeProtocol): void {
  form.value = applyProtocol(form.value, protocol);
}

function changePort(raw: string): void {
  form.value.port = raw === "" ? null : Number(raw);
}

async function submit(): Promise<void> {
  const errors = validateNodeForm(form.value);
  if (errors.length > 0) {
    showToast("error", errors[0]);
    return;
  }

  submitting.value = true;
  try {
    const payload = buildNodePayload(form.value);
    if (props.editing) {
      await adminApi().updateNode(props.editing.id, payload);
    } else {
      await adminApi().createNode(payload);
    }
    showToast("success", "已保存");
    emit("saved");
    emit("close");
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `保存失败：${(error as Error).message}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="title" @close="emit('close')">
    <div class="admin-form">
      <div class="admin-field">
        <label for="node-name">节点名</label>
        <input id="node-name" v-model="form.name" class="admin-input" placeholder="运维可读，如 LAND-东京-03" />
      </div>

      <p v-if="isSubscriptionNode" class="admin-note">
        订阅导入的节点（{{ props.editing?.sourceType }}，来自分组「{{ props.editing?.groupName }}」）。
        连接参数以订阅为准，改动请到分组上「重新拉取」；这里只能改名称、状态与备注。
      </p>

      <div v-if="!isSubscriptionNode" class="admin-form-row">
        <div class="admin-field">
          <label for="node-role">角色</label>
          <Select id="node-role" v-model="form.role" :options="roleOptions" aria-label="角色" />
        </div>
        <div class="admin-field">
          <label for="node-protocol">协议</label>
          <Select
            id="node-protocol"
            :model-value="form.protocol"
            :options="protocolOptions"
            aria-label="协议"
            mono
            @update:model-value="switchProtocol($event as NodeProtocol)"
          />
        </div>
      </div>

      <div v-if="!isSubscriptionNode" class="admin-form-row">
        <div class="admin-field">
          <label for="node-addr">地址</label>
          <input id="node-addr" v-model="form.serverAddr" class="admin-input fact" placeholder="tokyo.example.com" />
        </div>
        <div class="admin-field">
          <label for="node-port">端口</label>
          <input
            id="node-port"
            class="admin-input fact"
            type="number"
            min="1"
            max="65535"
            :value="form.port ?? ''"
            @input="changePort(($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>

      <div v-if="!isSubscriptionNode" class="admin-field">
        <label>敏感配置</label>
        <p class="admin-note">{{ secretKeyHint }}。这些值加密存储，服务端永不回传。</p>
        <input
          v-for="(_, key) in form.secret"
          :key="key"
          v-model="form.secret[key]"
          class="admin-input"
          type="password"
          :placeholder="`${key}（留空表示不修改）`"
          :aria-label="`敏感配置 ${key}`"
        />
      </div>

      <div v-if="!isSubscriptionNode" class="admin-field">
        <label>透传键</label>
        <p class="admin-note">明文存储并原样下发给 mihomo，不要在这里填密码。</p>
        <KeyValueEditor v-model="form.extraConfig" />
      </div>

      <div v-if="!isSubscriptionNode && form.role === 'LAND'" class="admin-field">
        <label for="node-egress">出口 IP</label>
        <input
          id="node-egress"
          v-model="form.egressIp"
          class="admin-input fact"
          placeholder="地址填 IP 时自动带入，可改"
        />
      </div>

      <div class="admin-form-row">
        <div class="admin-field">
          <label for="node-status">状态</label>
          <Select id="node-status" v-model="form.status" :options="statusOptions" aria-label="状态" />
        </div>
        <div class="admin-field">
          <label for="node-remark">备注</label>
          <input id="node-remark" v-model="form.remark" class="admin-input" />
        </div>
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
