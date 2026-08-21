<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { NodeGroupResponse, SubPreviewNode } from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";

// group 为 null 表示「贴新链接建分组」；非 null 表示对已有分组「重新拉取」
const props = defineProps<{ group: NodeGroupResponse | null }>();
const emit = defineEmits<{ close: []; saved: [] }>();

const subUrl = ref("");
const groupName = ref("");
const remark = ref("");
// null 表示还没拉过预览；拉过后进入勾选步骤
const 节点列表 = ref<SubPreviewNode[] | null>(null);
const 勾选 = ref<Set<string>>(new Set());
const loadingPreview = ref(false);
const submitting = ref(false);

const 标题 = computed(() => (props.group ? `重新拉取：${props.group.name}` : "从订阅导入节点"));
const 全选中 = computed(
  () => 节点列表.value !== null && 节点列表.value.length > 0 && 勾选.value.size === 节点列表.value.length,
);

async function 拉取预览(): Promise<void> {
  if (!props.group && !subUrl.value.trim()) {
    showToast("error", "先粘贴订阅链接");
    return;
  }
  loadingPreview.value = true;
  try {
    const list = props.group
      ? await adminApi().refreshPreviewNodeGroup(props.group.id)
      : await adminApi().previewSub({ subUrl: subUrl.value.trim() });
    节点列表.value = list;
    // 疑似信息条目与已入池节点默认不勾：前者多半是垃圾，后者勾选意味着「更新参数」，应显式为之
    勾选.value = new Set(list.filter((n) => !n.suspectedInfo && !n.existed).map((n) => n.sourceName));
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `拉取失败：${(error as Error).message}`);
  } finally {
    loadingPreview.value = false;
  }
}

function 切换勾选(name: string): void {
  const next = new Set(勾选.value);
  if (next.has(name)) {
    next.delete(name);
  } else {
    next.add(name);
  }
  勾选.value = next;
}

function 切换全选(): void {
  勾选.value = 全选中.value ? new Set() : new Set((节点列表.value ?? []).map((n) => n.sourceName));
}

async function 提交(): Promise<void> {
  if (勾选.value.size === 0) {
    showToast("error", "至少勾选一个节点");
    return;
  }
  if (!props.group && !groupName.value.trim()) {
    showToast("error", "给这个分组起个名字");
    return;
  }
  submitting.value = true;
  try {
    const selectedNames = [...勾选.value];
    if (props.group) {
      await adminApi().importNodeGroup(props.group.id, { selectedNames });
    } else {
      await adminApi().createNodeGroup({
        name: groupName.value.trim(),
        subUrl: subUrl.value.trim(),
        selectedNames,
        remark: remark.value.trim(),
      });
    }
    showToast("success", `已导入 ${selectedNames.length} 个节点`);
    emit("saved");
    emit("close");
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `导入失败：${(error as Error).message}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="标题" @close="emit('close')">
    <div class="admin-form">
      <div v-if="!props.group" class="admin-field">
        <label for="sub-url">订阅链接</label>
        <p class="admin-note">服务端会用 Clash UA 拉取并解析；链接含 token，将加密保存供以后重新拉取。</p>
        <input
          id="sub-url"
          v-model="subUrl"
          class="admin-input fact"
          placeholder="https://…?token=…"
          :disabled="节点列表 !== null"
        />
      </div>

      <button
        v-if="节点列表 === null"
        type="button"
        class="admin-btn"
        :disabled="loadingPreview"
        @click="拉取预览()"
      >
        {{ loadingPreview ? "拉取中…" : "拉取节点列表" }}
      </button>

      <template v-else>
        <div class="admin-field">
          <label>
            <input type="checkbox" :checked="全选中" aria-label="全选" @change="切换全选()" />
            节点（已选 {{ 勾选.size }} / {{ 节点列表.length }}）
          </label>
          <p class="admin-note">「疑似信息条目」与「已入池」默认不勾；勾选已入池的节点表示用订阅里的参数更新它。</p>
          <table class="admin-table">
            <tbody>
              <tr v-for="row in 节点列表" :key="row.sourceName" :class="{ muted: row.suspectedInfo }">
                <td>
                  <input
                    type="checkbox"
                    :checked="勾选.has(row.sourceName)"
                    :aria-label="`勾选 ${row.sourceName}`"
                    @change="切换勾选(row.sourceName)"
                  />
                </td>
                <td>{{ row.sourceName }}</td>
                <td class="fact">{{ row.sourceType }}</td>
                <td class="fact muted">{{ row.serverAddr }}:{{ row.port }}</td>
                <td>
                  <span v-if="row.existed" class="pill">已入池</span>
                  <span v-else-if="row.suspectedInfo" class="muted">疑似信息条目</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!props.group" class="admin-form-row">
          <div class="admin-field">
            <label for="group-name">分组名</label>
            <input id="group-name" v-model="groupName" class="admin-input" placeholder="如：机场A" />
          </div>
          <div class="admin-field">
            <label for="group-remark">备注</label>
            <input id="group-remark" v-model="remark" class="admin-input" />
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button
        v-if="节点列表 !== null"
        type="button"
        class="admin-btn"
        :disabled="submitting"
        @click="提交()"
      >
        {{ submitting ? "导入中…" : props.group ? "导入所选" : "创建分组并导入" }}
      </button>
    </template>
  </Modal>
</template>
