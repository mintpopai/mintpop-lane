<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AirportResponse, AirportSubscriptionResponse } from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

// group 为 null 表示「贴新链接建订阅」；非 null 表示对已有订阅「重新拉取」。
// 两种模式都不再逐个勾选：服务端拉取订阅后自动导入其中的美国节点（名称带 🇺🇸 或 [US]），
// 非美国节点与「剩余流量」这类信息条目一律略过
const props = defineProps<{ group: AirportSubscriptionResponse | null }>();
const emit = defineEmits<{ close: []; saved: [] }>();

const subUrl = ref("");
const groupName = ref("");
const remark = ref("");
const submitting = ref(false);

// 创建模式才需要选机场：重新拉取用的是已有订阅上早已定死的机场，不需要再选一次
const airports = ref<AirportResponse[]>([]);
const airportsLoaded = ref(false);
const airportId = ref<number | null>(null);
const account = ref("");
const bandwidth = ref("");

onMounted(async () => {
  if (props.group) {
    return;
  }
  try {
    airports.value = await adminApi().listAirports();
  } finally {
    airportsLoaded.value = true;
  }
});

const airportOptions = computed(() => airports.value.map((a) => ({ value: a.id, label: a.name })));
// 一家机场都没有时创建表单没法填出所属机场，与其让人填完才在提交时被拒，不如先挡住入口
const noAirport = computed(
  () => !props.group && airportsLoaded.value && airports.value.length === 0,
);

const title = computed(() => (props.group ? `重新拉取：${props.group.name}` : "从订阅导入节点"));

async function submit(): Promise<void> {
  if (!props.group) {
    if (!subUrl.value.trim()) {
      showToast("error", "先粘贴订阅链接");
      return;
    }
    if (!groupName.value.trim()) {
      showToast("error", "给这个订阅起个名字");
      return;
    }
    if (airportId.value === null) {
      showToast("error", "选择所属机场");
      return;
    }
    if (!account.value.trim()) {
      showToast("error", "填写购买该订阅的机场账号");
      return;
    }
    const bandwidthMbps = Number(bandwidth.value);
    if (!Number.isInteger(bandwidthMbps) || bandwidthMbps <= 0) {
      showToast("error", "带宽填大于 0 的整数（Mbps）");
      return;
    }
  }
  submitting.value = true;
  try {
    if (props.group) {
      await adminApi().importAirportSubscription(props.group.id);
    } else {
      await adminApi().createAirportSubscription({
        name: groupName.value.trim(),
        subUrl: subUrl.value.trim(),
        airportId: airportId.value as number,
        account: account.value.trim(),
        bandwidthMbps: Number(bandwidth.value),
        remark: remark.value.trim(),
      });
    }
    showToast("success", props.group ? "已重新拉取并导入美国节点" : "已创建订阅并导入美国节点");
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
        <p v-if="noAirport" class="admin-note">还没有机场。先到「机场」页新建机场，再回来导入。</p>
        <div class="admin-form-row">
          <div class="admin-field">
            <label for="sub-airport">所属机场</label>
            <Select
              id="sub-airport"
              v-model="airportId"
              :options="airportOptions"
              aria-label="所属机场"
            />
          </div>
          <div class="admin-field">
            <label for="sub-account">账号</label>
            <input
              id="sub-account"
              v-model="account"
              class="admin-input"
              placeholder="购买该订阅的机场账号"
            />
          </div>
        </div>
        <div class="admin-field">
          <label for="sub-bandwidth">带宽（Mbps）</label>
          <input
            id="sub-bandwidth"
            v-model="bandwidth"
            class="admin-input fact"
            inputmode="numeric"
            placeholder="如：300"
          />
          <p class="admin-note">
            订阅声明的总带宽，全部连接共享。每人按 20M 计主用名额；创建后不可改。
          </p>
        </div>
        <div class="admin-form-row">
          <div class="admin-field">
            <label for="group-name">订阅名</label>
            <input
              id="group-name"
              v-model="groupName"
              class="admin-input"
              placeholder="如：ts-01"
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
      <button type="button" class="admin-btn" :disabled="submitting || noAirport" @click="submit()">
        {{ submitting ? "导入中…" : props.group ? "重新拉取并导入" : "创建订阅并导入" }}
      </button>
    </template>
  </Modal>
</template>
