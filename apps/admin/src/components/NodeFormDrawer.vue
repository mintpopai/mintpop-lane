<script setup lang="ts">
import { ElMessage } from "element-plus";
import { computed, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { NODE_PROTOCOL, NODE_ROLE_LABELS, NODE_STATUS_LABELS } from "../api/types";
import type { AdminNodeResponse, NodeProtocol, NodeRole } from "../api/types";
import {
  applyProtocol,
  buildNodePayload,
  emptyNodeForm,
  nodeToForm,
  validateNodeForm,
  type NodeFormModel,
} from "../utils/nodeForm";
import KeyValueEditor from "./KeyValueEditor.vue";

const props = defineProps<{ modelValue: boolean; role: NodeRole; editing: AdminNodeResponse | null }>();
const emit = defineEmits<{ "update:modelValue": [boolean]; saved: [] }>();

const form = ref<NodeFormModel>(emptyNodeForm(props.role));
const submitting = ref(false);

const 标题 = computed(() => (props.editing ? `编辑节点：${props.editing.name}` : "新建节点"));
const 敏感键提示 = computed(() =>
  props.editing?.secretConfigured ? "已配置，留空表示不修改" : "尚未配置",
);

watch(
  () => props.modelValue,
  (opened) => {
    if (opened) {
      form.value = props.editing ? nodeToForm(props.editing) : emptyNodeForm(props.role);
    }
  },
);

function 切协议(protocol: NodeProtocol): void {
  form.value = applyProtocol(form.value, protocol);
}

async function 提交(): Promise<void> {
  const errors = validateNodeForm(form.value);
  if (errors.length > 0) {
    ElMessage.warning(errors[0]);
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
    ElMessage.success("已保存");
    emit("update:modelValue", false);
    emit("saved");
  } catch (error) {
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
    <el-form label-width="110px">
      <el-form-item label="节点名">
        <el-input v-model="form.name" placeholder="运维可读，如 LAND-东京-03" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.role">
          <el-option v-for="(label, value) in NODE_ROLE_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="协议">
        <el-select :model-value="form.protocol" @update:model-value="切协议">
          <el-option v-for="value in NODE_PROTOCOL" :key="value" :label="value" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="地址">
        <el-input v-model="form.serverAddr" placeholder="tokyo.example.com" />
      </el-form-item>
      <el-form-item label="端口">
        <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
      </el-form-item>

      <el-form-item label="敏感配置">
        <div style="width: 100%">
          <p style="margin: 0 0 8px; color: #909399">{{ 敏感键提示 }}。这些值加密存储，服务端永不回传。</p>
          <el-input
            v-for="(_, key) in form.secret"
            :key="key"
            v-model="form.secret[key]"
            type="password"
            show-password
            :placeholder="`${key}（留空表示不修改）`"
            style="margin-bottom: 8px"
          />
        </div>
      </el-form-item>

      <el-form-item label="透传键">
        <div style="width: 100%">
          <p style="margin: 0 0 8px; color: #909399">明文存储并原样下发给 mihomo，不要在这里填密码。</p>
          <KeyValueEditor v-model="form.extraConfig" />
        </div>
      </el-form-item>

      <el-form-item v-if="form.role === 'LAND'" label="出口 IP">
        <el-input v-model="form.egressIpsText" type="textarea" :rows="3" placeholder="一行一个" />
      </el-form-item>

      <el-form-item label="状态">
        <el-select v-model="form.status">
          <el-option v-for="(label, value) in NODE_STATUS_LABELS" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="提交()">保存</el-button>
    </template>
  </el-drawer>
</template>
