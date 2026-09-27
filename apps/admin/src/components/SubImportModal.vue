<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { NodeGroupResponse } from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";

// group 为 null 表示「贴新链接建分组」；非 null 表示对已有分组「重新拉取」。
// 两种模式都不再逐个勾选：服务端拉取订阅后自动导入其中的美国节点（名称带 🇺🇸 或 [US]），
// 非美国节点与「剩余流量」这类信息条目一律略过
const props = defineProps<{ group: NodeGroupResponse | null }>();
const emit = defineEmits<{ close: []; saved: [] }>();

const subUrl = ref("");
const groupName = ref("");
const remark = ref("");
const submitting = ref(false);

const title = computed(() => (props.group ? `重新拉取：${props.group.name}` : "从订阅导入节点"));

async function submit(): Promise<void> {
  if (!props.group) {
    if (!subUrl.value.trim()) {
      showToast("error", "先粘贴订阅链接");
      return;
    }
    if (!groupName.value.trim()) {
      showToast("error", "给这个分组起个名字");
      return;
    }
  }
  submitting.value = true;
  try {
    if (props.group) {
      await adminApi().importNodeGroup(props.group.id);
    } else {
      await adminApi().createNodeGroup({
        name: groupName.value.trim(),
        subUrl: subUrl.value.trim(),
        remark: remark.value.trim(),
      });
    }
    showToast("success", props.group ? "已重新拉取并导入美国节点" : "已创建分组并导入美国节点");
    emit("saved");
    emit("close");
  } catch (error) {
    showToast(
      "error",
      error instanceof BizError ? error.message : `导入失败：${(error as Error).message}`,
    );
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="title" @close="emit('close')">
    <div class="admin-form">
      <p class="admin-note">自动导入订阅里的美国节点（名称带 🇺🇸 或 [US]），其余节点不导入。</p>
      <template v-if="!props.group">
        <div class="admin-field">
          <label for="sub-url">订阅链接</label>
          <p class="admin-note">
            服务端会用 Clash UA 拉取并解析；链接含 token，将加密保存供以后重新拉取。
          </p>
          <input
            id="sub-url"
            v-model="subUrl"
            class="admin-input fact"
            placeholder="https://…?token=…"
          />
        </div>
        <div class="admin-form-row">
          <div class="admin-field">
            <label for="group-name">分组名</label>
            <input
              id="group-name"
              v-model="groupName"
              class="admin-input"
              placeholder="如：机场A"
            />
          </div>
          <div class="admin-field">
            <label for="group-remark">备注</label>
            <input id="group-remark" v-model="remark" class="admin-input" />
          </div>
        </div>
      </template>
      <p v-else class="admin-note">
        将用保存的订阅链接重新拉取：已入池的美国节点更新参数，新出现的美国节点入池。
      </p>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="submitting" @click="submit()">
        {{ submitting ? "导入中…" : props.group ? "重新拉取并导入" : "创建分组并导入" }}
      </button>
    </template>
  </Modal>
</template>
