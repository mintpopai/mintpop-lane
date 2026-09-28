<script setup lang="ts">
import { computed, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AirportResponse } from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";

const props = defineProps<{ editing: AirportResponse | null }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即初始化表单
const emit = defineEmits<{ close: []; saved: [] }>();

const name = ref(props.editing?.name ?? "");
const websiteUrl = ref(props.editing?.websiteUrl ?? "");
const remark = ref(props.editing?.remark ?? "");
const submitting = ref(false);

const title = computed(() => (props.editing ? `编辑机场：${props.editing.name}` : "新建机场"));

async function submit(): Promise<void> {
  if (!name.value.trim()) {
    showToast("error", "填写机场名称");
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      name: name.value.trim(),
      websiteUrl: websiteUrl.value.trim(),
      remark: remark.value.trim(),
    };
    if (props.editing) {
      await adminApi().updateAirport(props.editing.id, payload);
    } else {
      await adminApi().createAirport(payload);
    }
    showToast("success", "已保存");
    emit("saved");
    emit("close");
  } catch (error) {
    showToast(
      "error",
      error instanceof BizError ? error.message : `保存失败：${(error as Error).message}`,
    );
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Modal :title="title" @close="emit('close')">
    <div class="admin-form">
      <div class="admin-field">
        <label for="airport-name">机场名称</label>
        <input id="airport-name" v-model="name" class="admin-input" placeholder="如：泰山云" />
      </div>
      <div class="admin-field">
        <label for="airport-url">机场地址</label>
        <input
          id="airport-url"
          v-model="websiteUrl"
          class="admin-input fact"
          placeholder="官网或用户中心地址，可空"
        />
      </div>
      <div class="admin-field">
        <label for="airport-remark">备注</label>
        <input
          id="airport-remark"
          v-model="remark"
          class="admin-input"
          placeholder="管理员自用说明，可空"
        />
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
