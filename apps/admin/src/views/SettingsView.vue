<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type {
  FrontRebuildPreview,
  FrontRebuildStatus,
  FrontSettingsResponse,
  NodeRegion,
} from "../api/types";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import Select from "../components/AdminSelect.vue";
import { showToast } from "../toast";
import { formatDateTime } from "../utils/format";

/** 轮询重算状态的间隔；只在 RUNNING 时轮询 */
const STATUS_POLL_MS = 3000;

const loading = ref(true);
const loadError = ref("");
const saved = ref<FrontSettingsResponse | null>(null);

const region = ref<NodeRegion>("US");
const airportsPerUser = ref("3");
const bandwidthPerUserMbps = ref("20");

const status = ref<FrontRebuildStatus | null>(null);
const preview = ref<FrontRebuildPreview | null>(null);

/** 待确认的动作：保存设置 或 手动重算 */
const pendingAction = ref<"SAVE" | "REBUILD" | null>(null);
const submitting = ref(false);

let pollTimer: ReturnType<typeof setInterval> | undefined;
/** 卸载后异步返回的 load/poll 不许再起定时器 */
let unmounted = false;

const regionOptions = computed(() =>
  (saved.value?.regionOptions ?? []).map((o) => ({ value: o.value, label: o.label })),
);

const airportsNumber = computed(() => Number(airportsPerUser.value));
const bandwidthNumber = computed(() => Number(bandwidthPerUserMbps.value));

/** 「1 条主线路，n-1 条备用线路」随输入实时变；非法输入不显示 */
const airportsHint = computed(() => {
  const n = airportsNumber.value;
  if (!Number.isInteger(n) || n < 1) {
    return "";
  }
  return `1 条主线路，${n - 1} 条备用线路`;
});

const dirty = computed(
  () =>
    !!saved.value &&
    (region.value !== saved.value.region ||
      airportsNumber.value !== saved.value.airportsPerUser ||
      bandwidthNumber.value !== saved.value.bandwidthPerUserMbps),
);

const running = computed(() => status.value?.phase === "RUNNING");

const previewText = computed(() =>
  preview.value
    ? `需要 ${preview.value.requiredPrimary} 个主用名额，现有 ${preview.value.availablePrimary}`
    : "",
);

const confirmMessage = computed(() => {
  const head =
    pendingAction.value === "SAVE"
      ? "保存后将重新拉取全部订阅并为所有用户重新分配线路，客户端会自动热更新。"
      : "将重新拉取全部订阅并为所有用户重新分配线路，客户端会自动热更新。";
  if (!preview.value) {
    return `${head} 正在预检容量…`;
  }
  return preview.value.sufficient
    ? `${head} ${previewText.value}，容量足够。确认继续？`
    : `${head} ${previewText.value}，主用名额不足，无法继续。请先补充订阅。`;
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [s, st] = await Promise.all([
      adminApi().getFrontSettings(),
      adminApi().frontRebuildStatus(),
    ]);
    saved.value = s;
    region.value = s.region;
    airportsPerUser.value = String(s.airportsPerUser);
    bandwidthPerUserMbps.value = String(s.bandwidthPerUserMbps);
    status.value = st;
    loadError.value = "";
    await refreshPreview("FORM");
    syncPolling();
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

/**
 * 容量预检。来源二选一：
 * - FORM：按表单里的当前值算（页面常显、保存前确认）
 * - SAVED：按已保存的配置算（手动重算跑的是已保存值，不是表单里没保存的改动）
 */
async function refreshPreview(source: "FORM" | "SAVED"): Promise<void> {
  const target =
    source === "SAVED" && saved.value
      ? { region: saved.value.region, bandwidth: saved.value.bandwidthPerUserMbps }
      : { region: region.value, bandwidth: bandwidthNumber.value };
  if (!Number.isInteger(target.bandwidth) || target.bandwidth < 1) {
    preview.value = null;
    return;
  }
  try {
    preview.value = await adminApi().previewFrontRebuild(target.region, target.bandwidth);
  } catch (error) {
    preview.value = null;
    showToast(
      "error",
      error instanceof BizError ? error.message : `预检失败：${(error as Error).message}`,
    );
  }
}

watch([region, bandwidthPerUserMbps], () => {
  void refreshPreview("FORM");
});

async function pollStatus(): Promise<void> {
  try {
    status.value = await adminApi().frontRebuildStatus();
    syncPolling();
  } catch {
    // 轮询失败不打扰，下一次再试
  }
}

/** RUNNING 才开轮询，结束就停：别让一个空闲页面每 3 秒打服务端 */
function syncPolling(): void {
  if (unmounted) {
    return;
  }
  if (running.value && pollTimer === undefined) {
    pollTimer = setInterval(() => void pollStatus(), STATUS_POLL_MS);
  } else if (!running.value && pollTimer !== undefined) {
    clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function askSave(): void {
  const n = airportsNumber.value;
  const b = bandwidthNumber.value;
  if (!Number.isInteger(n) || n < 1 || n > 10 || !Number.isInteger(b) || b < 1 || b > 1000) {
    showToast("error", "每人机场数 1 到 10，每人带宽 1 到 1000 Mbps");
    return;
  }
  pendingAction.value = "SAVE";
  void refreshPreview("FORM");
}

/** 表单回到已保存的值；预检随 watch 自动按回退后的值重算 */
function revert(): void {
  if (!saved.value) {
    return;
  }
  region.value = saved.value.region;
  airportsPerUser.value = String(saved.value.airportsPerUser);
  bandwidthPerUserMbps.value = String(saved.value.bandwidthPerUserMbps);
}

function askRebuild(): void {
  pendingAction.value = "REBUILD";
  void refreshPreview("SAVED");
}

/** 关弹窗；常显的预检回到按表单值算 */
function cancelAction(): void {
  const wasRebuild = pendingAction.value === "REBUILD";
  pendingAction.value = null;
  if (wasRebuild) {
    void refreshPreview("FORM");
  }
}

async function confirmAction(): Promise<void> {
  if (!pendingAction.value || !preview.value?.sufficient) {
    return;
  }
  submitting.value = true;
  try {
    if (pendingAction.value === "SAVE") {
      const changed = dirty.value;
      saved.value = await adminApi().updateFrontSettings({
        region: region.value,
        airportsPerUser: airportsNumber.value,
        bandwidthPerUserMbps: bandwidthNumber.value,
      });
      showToast("success", changed ? "已保存，正在为全部用户重算线路" : "已保存");
    } else {
      await adminApi().startFrontRebuild();
      showToast("success", "已开始为全部用户重算线路");
    }
    pendingAction.value = null;
    await pollStatus();
  } catch (error) {
    // 410054 取值非法、410055 正在重算，服务端给的中文提示直接用
    showToast(
      "error",
      error instanceof BizError ? error.message : `操作失败：${(error as Error).message}`,
    );
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
onBeforeUnmount(() => {
  unmounted = true;
  clearInterval(pollTimer);
  pollTimer = undefined;
});
</script>

<template>
  <PageHead title="全局配置">
    <template #facts>
      机场订阅线路的全局参数。任一项改动都会重新拉取全部订阅并为所有用户重新分配线路。
    </template>
    <template #actions>
      <button
        type="button"
        class="admin-btn-ghost rebuild"
        :disabled="running || dirty"
        @click="askRebuild()"
      >
        {{ running ? "重算中…" : "重算全部线路" }}
      </button>
      <p v-if="dirty" class="admin-note">先保存或还原改动后再重算</p>
    </template>
  </PageHead>

  <!-- 设置行「左说明 · 右控件」：说明是管理员照着做决定的依据，放在控件旁边比挤在控件下方好读；
       控件收窄到固定宽度，数字框不再横穿整张卡 -->
  <DataCard class="settings-card" :loading="loading" :error="loadError" :empty="false">
    <header class="card-head">
      <h3 class="card-title">线路参数</h3>
      <span v-if="dirty" class="pill pending">有未保存的改动</span>
    </header>

    <div class="setting-row">
      <div class="setting-text">
        <label for="setting-region">筛选地区</label>
        <p class="admin-note">只把落在该地区的节点作为分配候选。现在只有美国。</p>
      </div>
      <div class="setting-control">
        <Select
          id="setting-region"
          v-model="region"
          :options="regionOptions"
          aria-label="筛选地区"
        />
      </div>
    </div>

    <div class="setting-row">
      <div class="setting-text">
        <label for="setting-airports">每个用户分配几家机场的订阅</label>
        <p class="admin-note">{{ airportsHint || "填 1 到 10 的整数" }}</p>
      </div>
      <div class="setting-control">
        <div class="unit-input">
          <input
            id="setting-airports"
            v-model="airportsPerUser"
            class="admin-input fact"
            type="number"
            min="1"
            max="10"
          />
          <span class="unit">家</span>
        </div>
      </div>
    </div>

    <div class="setting-row">
      <div class="setting-text">
        <label for="setting-bandwidth">每个用户按多少带宽计名额</label>
        <p class="admin-note">订阅主用名额 = 订阅带宽 ÷ 此值，向下取整。</p>
      </div>
      <div class="setting-control">
        <div class="unit-input">
          <input
            id="setting-bandwidth"
            v-model="bandwidthPerUserMbps"
            class="admin-input fact"
            type="number"
            min="1"
            max="1000"
          />
          <span class="unit">Mbps</span>
        </div>
      </div>
    </div>

    <!-- 底栏：左边是按表单值算的容量预检（保存前就能看到够不够），右边是还原与保存 -->
    <footer class="settings-foot">
      <div class="capacity">
        <span class="capacity-label">容量预检</span>
        <template v-if="preview">
          <span class="fact">{{ previewText }}</span>
          <span class="state" :data-state="preview.sufficient ? 'ENABLED' : 'REVOKED'">{{
            preview.sufficient ? "容量足够" : "名额不足"
          }}</span>
        </template>
        <span v-else class="muted">—</span>
      </div>
      <div class="foot-actions">
        <button
          v-if="dirty"
          type="button"
          class="admin-btn-ghost revert"
          :disabled="submitting"
          @click="revert()"
        >
          还原
        </button>
        <button
          type="button"
          class="admin-btn save"
          :disabled="!dirty || running || submitting"
          @click="askSave()"
        >
          保存
        </button>
      </div>
    </footer>
  </DataCard>

  <section v-if="!loading && !loadError" class="admin-card status-card">
    <h3 class="card-title">最近一次重算</h3>
    <p v-if="!status || status.phase === 'IDLE'" class="muted">服务端启动以来还没有重算过。</p>
    <p v-else-if="status.phase === 'RUNNING'" class="status-line">
      <span class="state" data-state="ENABLED">重算中</span>
      <span class="fact muted">开始于 {{ formatDateTime(status.startedAt) }}</span>
    </p>
    <p v-else-if="status.phase === 'SUCCEEDED'" class="status-line">
      <span class="state" data-state="ENABLED">上次重算成功</span>
      <span
        ><span class="fact">{{ status.userCount }}</span> 个用户、<span class="fact">{{
          status.subscriptionCount
        }}</span>
        个订阅</span
      >
      <span class="fact muted">完成于 {{ formatDateTime(status.finishedAt) }}</span>
    </p>
    <p v-else class="status-line">
      <span class="state" data-state="REVOKED">上次重算失败</span>
      <span>{{ status.error }}</span>
      <span class="fact muted">{{ formatDateTime(status.finishedAt) }}</span>
    </p>
  </section>

  <ConfirmDialog
    v-if="pendingAction"
    :title="pendingAction === 'SAVE' ? '保存并重算全部线路' : '重算全部线路'"
    :message="confirmMessage"
    confirm-text="确认"
    :busy="submitting"
    :confirm-disabled="!preview || !preview.sufficient"
    @confirm="confirmAction()"
    @cancel="cancelAction()"
  />
</template>

<style scoped>
.settings-card {
  padding: 4px 24px 0;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}

/* 一行一个参数：左说明右控件，行与行之间一条发丝线 */
.setting-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 32px;
  align-items: center;
  padding: 20px 0;
  border-top: 1px solid var(--color-border);
}

.setting-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.setting-text > label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink);
}

.setting-control > * {
  width: 100%;
}

/* 单位贴在数字框内右侧：挂在框外会让数字框比地区下拉短一截，三个控件右缘对不齐。
   单位不接收点击，点到它等于点输入框 */
.unit-input {
  position: relative;
}

.unit-input .admin-input {
  width: 100%;
  padding-right: 56px;
}

.unit {
  position: absolute;
  top: 50%;
  right: 12px;
  transform: translateY(-50%);
  font-size: 13px;
  color: var(--color-ink-secondary);
  pointer-events: none;
}

/* 底栏铺 Cloud 底并贴满卡片左右下缘，和参数区分成两层：上面是「填什么」，下面是「会怎样」 */
.settings-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0 -24px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border);
  background: var(--color-bg-cloud);
}

.capacity {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.capacity-label {
  color: var(--color-ink-secondary);
}

.foot-actions {
  display: flex;
  gap: 8px;
}

.muted {
  color: var(--color-ink-secondary);
}

.status-card {
  margin-top: 16px;
  padding: 20px 24px;
}

.status-card .card-title {
  margin-bottom: 12px;
}

.status-card p {
  font-size: 14px;
}

.status-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
}

.status-line .fact.muted {
  font-size: 13px;
  font-weight: 400;
}

@media (max-width: 720px) {
  .setting-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
}
</style>
