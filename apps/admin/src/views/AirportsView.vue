<script setup lang="ts">
import { onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AirportResponse } from "../api/types";
import AirportFormModal from "../components/AirportFormModal.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import TruncatedText from "../components/TruncatedText.vue";
import { showToast } from "../toast";
import { formatDateTime } from "../utils/format";

const airports = ref<AirportResponse[]>([]);
const loading = ref(true);
const loadError = ref("");
const modalOpen = ref(false);
const editing = ref<AirportResponse | null>(null);
const pendingDelete = ref<AirportResponse | null>(null);
const deleting = ref(false);

async function load(): Promise<void> {
  loading.value = true;
  try {
    airports.value = await adminApi().listAirports();
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

function create(): void {
  editing.value = null;
  modalOpen.value = true;
}

function edit(airport: AirportResponse): void {
  editing.value = airport;
  modalOpen.value = true;
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value) {
    return;
  }
  deleting.value = true;
  try {
    await adminApi().deleteAirport(pendingDelete.value.id);
    showToast("success", "已删除");
    pendingDelete.value = null;
    await load();
  } catch (error) {
    // 410053 机场下还有订阅，服务端给的中文提示直接用
    showToast(
      "error",
      error instanceof BizError ? error.message : `删除失败：${(error as Error).message}`,
    );
  } finally {
    deleting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <PageHead title="机场">
    <template #facts>
      共
      <span class="fact">{{ airports.length }}</span>
      家。每个用户的第一跳在每家机场各分到一个订阅，主用占名额、备用不占。
    </template>
    <template #actions>
      <button type="button" class="admin-btn" @click="create()">新建机场</button>
    </template>
  </PageHead>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="airports.length === 0"
    empty-text="还没有机场。先建机场，再到节点池从订阅导入。"
  >
    <template #empty-action>
      <button type="button" class="admin-btn" @click="create()">新建机场</button>
    </template>

    <table class="admin-table sticky-actions">
      <thead>
        <tr>
          <th>机场名称</th>
          <th>地址</th>
          <th>订阅数</th>
          <th>主用名额</th>
          <th>备注</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in airports" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="fact">
            <a v-if="row.websiteUrl" :href="row.websiteUrl" target="_blank" rel="noopener">{{
              row.websiteUrl
            }}</a>
            <span v-else class="muted">—</span>
          </td>
          <td class="fact">{{ row.subscriptionCount }}</td>
          <td class="fact">{{ row.primaryUsed }} / {{ row.primaryCapacity }}</td>
          <td class="muted"><TruncatedText :text="row.remark" /></td>
          <td class="fact muted">{{ formatDateTime(row.updatedAt) }}</td>
          <td class="actions">
            <button type="button" class="admin-link" @click="edit(row)">编辑</button>
            <button type="button" class="admin-link danger" @click="pendingDelete = row">
              删除
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </DataCard>

  <AirportFormModal
    v-if="modalOpen"
    :editing="editing"
    @saved="load()"
    @close="modalOpen = false"
  />
  <ConfirmDialog
    v-if="pendingDelete"
    title="删除确认"
    :message="`确认删除机场「${pendingDelete.name}」？机场下还有订阅时无法删除。`"
    :busy="deleting"
    @confirm="confirmDelete()"
    @cancel="pendingDelete = null"
  />
</template>
