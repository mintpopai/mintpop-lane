<script setup lang="ts">
import { ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AirportSubscriptionResponse } from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";

// 所属机场与带宽创建后不可改：改机场会破坏「每家机场最多一次」，改带宽会让已分配人数与容量对不上
const props = defineProps<{ subscription: AirportSubscriptionResponse }>();
const emit = defineEmits<{ close: []; saved: [] }>();

const name = ref(props.subscription.name);
const account = ref(props.subscription.account);
const remark = ref(props.subscription.remark ?? "");
const submitting = ref(false);

async function submit(): Promise<void> {
  if (!name.value.trim() || !account.value.trim()) {
    showToast("error", "订阅名与账号都不能为空");
    return;
  }
  submitting.value = true;
  try {
    await adminApi().updateAirportSubscription(props.subscription.id, {
      name: name.value.trim(),
      account: account.value.trim(),
      remark: remark.value.trim(),
    });
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
  <Modal :title="`编辑订阅：${props.subscription.name}`" @close="emit('close')">
    <div class="admin-form">
      <p class="admin-note">
        所属机场 <span class="fact">{{ props.subscription.airportName }}</span> · 带宽
        <span class="fact">{{ props.subscription.bandwidthMbps }} Mbps</span>，创建后不可改。
      </p>
      <div class="admin-field">
        <label for="sub-edit-name">订阅名</label>
        <input id="sub-edit-name" v-model="name" class="admin-input" />
      </div>
      <div class="admin-field">
        <label for="sub-edit-account">账号</label>
        <input id="sub-edit-account" v-model="account" class="admin-input" />
      </div>
      <div class="admin-field">
        <label for="sub-edit-remark">备注</label>
        <input id="sub-edit-remark" v-model="remark" class="admin-input" />
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
