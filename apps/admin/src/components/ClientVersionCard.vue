<script setup lang="ts">
// 桌面端最新版本卡片：服务端每分钟从更新清单拉一次最新版本，落后于它的桌面端会被强制更新。
// 发版后不想等下一轮定时拉取，可以在这里手动立即拉取。
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { ClientVersionStatus } from "../api/types";
import { showToast } from "../toast";
import { formatDateTime } from "../utils/format";

const status = ref<ClientVersionStatus | null>(null);
const loadError = ref("");
const refreshing = ref(false);

const versionText = computed(() =>
  status.value?.latest ? `v${status.value.latest}` : "尚未拉取到",
);

function errorText(error: unknown, prefix: string): string {
  return error instanceof BizError ? error.message : `${prefix}：${(error as Error).message}`;
}

async function load(): Promise<void> {
  try {
    status.value = await adminApi().getClientVersion();
    loadError.value = "";
  } catch (error) {
    loadError.value = errorText(error, "加载失败");
  }
}

async function refresh(): Promise<void> {
  refreshing.value = true;
  try {
    const previous = status.value?.latest ?? null;
    status.value = await adminApi().refreshClientVersion();
    loadError.value = "";
    if (status.value.lastAttemptFailed) {
      showToast(
        "error",
        status.value.latest
          ? `拉取失败，仍沿用 v${status.value.latest}`
          : "拉取失败，暂时不知道最新版本",
      );
    } else if (status.value.latest !== previous) {
      showToast("success", `最新版本已更新为 v${status.value.latest}`);
    } else {
      showToast("success", `已是最新：v${status.value.latest}`);
    }
  } catch (error) {
    showToast("error", errorText(error, "拉取失败"));
  } finally {
    refreshing.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="admin-card version-card">
    <header class="card-head">
      <h3 class="card-title">桌面端版本</h3>
      <button
        type="button"
        class="admin-btn-ghost refresh"
        :disabled="refreshing || !!loadError"
        @click="refresh()"
      >
        {{ refreshing ? "拉取中…" : "立即拉取" }}
      </button>
    </header>
    <p class="admin-note">
      服务端每分钟从更新清单拉取一次最新版本，低于它的桌面端会被强制更新。发版后可手动立即拉取。
    </p>

    <p v-if="loadError" class="load-error" role="alert">{{ loadError }}</p>
    <dl v-else-if="status" class="facts">
      <div class="fact-row">
        <dt>最新版本</dt>
        <dd>
          <span class="fact version" :class="{ muted: !status.latest }">{{ versionText }}</span>
          <span v-if="status.lastAttemptFailed" class="state" data-state="REVOKED"
            >最近一次拉取失败</span
          >
        </dd>
      </div>
      <div class="fact-row">
        <dt>上次拉取成功</dt>
        <dd class="fact">{{ formatDateTime(status.fetchedAt) }}</dd>
      </div>
      <div class="fact-row">
        <dt>最近一次尝试</dt>
        <dd class="fact">{{ formatDateTime(status.lastAttemptAt) }}</dd>
      </div>
      <div class="fact-row">
        <dt>更新清单</dt>
        <dd class="fact url">{{ status.manifestUrl }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.version-card {
  margin-top: 16px;
  padding: 20px 24px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}

.facts {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.fact-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  font-size: 14px;
}

.fact-row dt {
  color: var(--color-ink-secondary);
}

.fact-row dd {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.version {
  font-weight: 600;
}

.url {
  overflow-wrap: anywhere;
  font-size: 13px;
}

.muted {
  color: var(--color-ink-secondary);
}

.load-error {
  margin-top: 12px;
  font-size: 14px;
  color: var(--counter-danger);
}

@media (max-width: 720px) {
  .fact-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
}
</style>
