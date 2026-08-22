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
const nodeList = ref<SubPreviewNode[] | null>(null);
const checked = ref<Set<string>>(new Set());
const loadingPreview = ref(false);
const submitting = ref(false);

const title = computed(() => (props.group ? `重新拉取：${props.group.name}` : "从订阅导入节点"));
const allChecked = computed(
  () => nodeList.value !== null && nodeList.value.length > 0 && checked.value.size === nodeList.value.length,
);

async function fetchPreview(): Promise<void> {
  if (!props.group && !subUrl.value.trim()) {
    showToast("error", "先粘贴订阅链接");
    return;
  }
  loadingPreview.value = true;
  try {
    const list = props.group
      ? await adminApi().refreshPreviewNodeGroup(props.group.id)
      : await adminApi().previewSub({ subUrl: subUrl.value.trim() });
    nodeList.value = list;
    // 疑似信息条目与已入池节点默认不勾：前者多半是垃圾，后者勾选意味着「更新参数」，应显式为之
    checked.value = new Set(list.filter((n) => !n.suspectedInfo && !n.existed).map((n) => n.sourceName));
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : `拉取失败：${(error as Error).message}`);
  } finally {
    loadingPreview.value = false;
  }
}

function toggleCheck(name: string): void {
  const next = new Set(checked.value);
  if (next.has(name)) {
    next.delete(name);
  } else {
    next.add(name);
  }
  checked.value = next;
}

function toggleCheckAll(): void {
  checked.value = allChecked.value ? new Set() : new Set((nodeList.value ?? []).map((n) => n.sourceName));
}

async function submit(): Promise<void> {
  if (checked.value.size === 0) {
    showToast("error", "至少勾选一个节点");
    return;
  }
  if (!props.group && !groupName.value.trim()) {
    showToast("error", "给这个分组起个名字");
    return;
  }
  submitting.value = true;
  try {
    const selectedNames = [...checked.value];
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
  <!-- 贴链接那步内容只有一个输入框，窄款正合适；拉到节点列表后表格有五列，切成宽款 -->
  <Modal :title="title" :wide="nodeList !== null" @close="emit('close')">
    <div class="admin-form">
      <div v-if="!props.group" class="admin-field">
        <label for="sub-url">订阅链接</label>
        <p class="admin-note">服务端会用 Clash UA 拉取并解析；链接含 token，将加密保存供以后重新拉取。</p>
        <input
          id="sub-url"
          v-model="subUrl"
          class="admin-input fact"
          placeholder="https://…?token=…"
          :disabled="nodeList !== null"
        />
      </div>
      <p v-else-if="nodeList === null" class="admin-note">将用保存的订阅链接重新拉取该分组的节点列表。</p>

      <template v-if="nodeList !== null">
        <div class="admin-field">
          <p class="admin-note">「疑似信息条目」与「已入池」默认不勾；勾选已入池的节点表示用订阅里的参数更新它。</p>
          <div class="node-panel">
            <div class="node-panel-bar">
              <label class="node-check-all">
                <input type="checkbox" :checked="allChecked" @change="toggleCheckAll()" />
                全选
              </label>
              <span class="node-count">已选 <span class="fact">{{ checked.size }} / {{ nodeList.length }}</span></span>
            </div>
            <div class="node-panel-body">
              <table class="admin-table dense node-table">
                <thead>
                  <tr>
                    <th class="col-check"></th>
                    <th>名称</th>
                    <th>类型</th>
                    <th>服务器</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in nodeList"
                    :key="row.sourceName"
                    :class="{ dim: row.suspectedInfo }"
                    @click="toggleCheck(row.sourceName)"
                  >
                    <td class="col-check">
                      <input
                        type="checkbox"
                        :checked="checked.has(row.sourceName)"
                        :aria-label="`勾选 ${row.sourceName}`"
                        @click.stop
                        @change="toggleCheck(row.sourceName)"
                      />
                    </td>
                    <td class="col-name" :title="row.sourceName">{{ row.sourceName }}</td>
                    <td class="fact">{{ row.sourceType }}</td>
                    <td class="fact muted">{{ row.serverAddr }}:{{ row.port }}</td>
                    <td>
                      <span v-if="row.existed" class="pill">已入池</span>
                      <span v-else-if="row.suspectedInfo" class="pill muted">疑似信息条目</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
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
        v-if="nodeList === null"
        type="button"
        class="admin-btn"
        :disabled="loadingPreview"
        @click="fetchPreview()"
      >
        {{ loadingPreview ? "拉取中…" : "拉取节点列表" }}
      </button>
      <button
        v-else
        type="button"
        class="admin-btn"
        :disabled="submitting"
        @click="submit()"
      >
        {{ submitting ? "导入中…" : props.group ? "导入所选" : "创建分组并导入" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
/* —— 节点勾选面板：工具条 + 限高滚动的表格区。
   节点动辄几十上百个，列表自己滚，别把全选和下方的分组表单挤出弹窗视野 —— */
.node-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  overflow: hidden;
}

.node-panel-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--color-bg-cloud);
  border-bottom: 1px solid var(--color-border);
}

.node-check-all {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-ink);
  cursor: pointer;
}

.node-count {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.node-count .fact {
  color: var(--color-ink);
}

.node-panel-body {
  max-height: 45vh;
  overflow-y: auto;
}

/* 合并边框的表格里 sticky 表头的边框不跟着钉住（Chrome/Safari），改用分离边框 */
.node-table {
  border-collapse: separate;
  border-spacing: 0;
}

.node-table thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-bg);
}

/* 整行都是勾选的点击区 */
.node-table tbody tr {
  cursor: pointer;
}

.node-table input[type='checkbox'] {
  accent-color: var(--counter-focus);
  cursor: pointer;
}

.node-table .col-check {
  width: 1%;
  padding-right: 0;
}

/* 富余宽度给名称列而不是默认的最后一列；width:100% + max-width:0 让超长名称按省略号截断 */
.node-table tr > *:last-child {
  width: auto;
}

.node-table .col-name {
  width: 100%;
  max-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 疑似信息条目整行淡化：它多半不是节点，视觉上先降一档 */
.node-table tr.dim td {
  color: var(--color-ink-secondary);
}
</style>
