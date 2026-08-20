<script setup lang="ts">
import { computed, ref } from "vue";
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

const 标题 = computed(() => (props.editing ? `编辑节点：${props.editing.name}` : "新建节点"));
const 角色选项 = Object.entries(NODE_ROLE_LABELS).map(([value, label]) => ({ value, label }));
// 协议名是系统枚举值，下拉走等宽（mono），与表格里同一个值的排版对齐
const 协议选项 = Object.values(NODE_PROTOCOL).map((value) => ({ value, label: value }));
const 状态选项 = Object.entries(NODE_STATUS_LABELS).map(([value, label]) => ({ value, label }));

const 协议已变更 = computed(
  () => props.editing !== null && props.editing.protocol !== form.value.protocol,
);
const 敏感键提示 = computed(() => {
  // 协议一变，旧密钥就不再适用；此时若还显示「留空表示不修改」，管理员会以为留空是安全的
  if (协议已变更.value) {
    return "协议已变更，必须重新填写——留空会让服务端继续沿用旧协议的密钥";
  }
  return props.editing?.secretConfigured ? "已配置，留空表示不修改" : "尚未配置";
});

function 切协议(protocol: NodeProtocol): void {
  form.value = applyProtocol(form.value, protocol);
}

function 改端口(raw: string): void {
  form.value.port = raw === "" ? null : Number(raw);
}

async function 提交(): Promise<void> {
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
  <Modal :title="标题" @close="emit('close')">
    <div class="admin-form">
      <div class="admin-field">
        <label for="node-name">节点名</label>
        <input id="node-name" v-model="form.name" class="admin-input" placeholder="运维可读，如 LAND-东京-03" />
      </div>

      <div class="admin-form-row">
        <div class="admin-field">
          <label for="node-role">角色</label>
          <Select id="node-role" v-model="form.role" :options="角色选项" aria-label="角色" />
        </div>
        <div class="admin-field">
          <label for="node-protocol">协议</label>
          <Select
            id="node-protocol"
            :model-value="form.protocol"
            :options="协议选项"
            aria-label="协议"
            mono
            @update:model-value="切协议($event as NodeProtocol)"
          />
        </div>
      </div>

      <div class="admin-form-row">
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
            @input="改端口(($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>

      <div class="admin-field">
        <label>敏感配置</label>
        <p class="admin-note">{{ 敏感键提示 }}。这些值加密存储，服务端永不回传。</p>
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

      <div class="admin-field">
        <label>透传键</label>
        <p class="admin-note">明文存储并原样下发给 mihomo，不要在这里填密码。</p>
        <KeyValueEditor v-model="form.extraConfig" />
      </div>

      <div v-if="form.role === 'LAND'" class="admin-field">
        <label for="node-egress">出口 IP</label>
        <textarea
          id="node-egress"
          v-model="form.egressIpsText"
          class="admin-textarea fact"
          rows="3"
          placeholder="一行一个"
        ></textarea>
      </div>

      <div class="admin-form-row">
        <div class="admin-field">
          <label for="node-status">状态</label>
          <Select id="node-status" v-model="form.status" :options="状态选项" aria-label="状态" />
        </div>
        <div class="admin-field">
          <label for="node-remark">备注</label>
          <input id="node-remark" v-model="form.remark" class="admin-input" />
        </div>
      </div>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="submitting" @click="提交()">
        {{ submitting ? "保存中…" : "保存" }}
      </button>
    </template>
  </Modal>
</template>
