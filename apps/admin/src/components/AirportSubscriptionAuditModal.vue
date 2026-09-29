<script setup lang="ts">
// 采购尽调：给一个候选机场的试用订阅链接，判断它是否与库里已有节点撞故障域。
// 全程只读、不落库，与「重新拉取」「导入」这类会改订阅数据的弹窗不同，故不 emit saved，
// 不需要父级 load() 刷新任何东西。
import { ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { SubAuditResponse } from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";

const emit = defineEmits<{ close: [] }>();

const subUrl = ref("");
const report = ref<SubAuditResponse | null>(null);
const auditing = ref(false);

async function submit(): Promise<void> {
  if (!subUrl.value.trim()) {
    showToast("error", "先粘贴候选机场的订阅链接");
    return;
  }
  auditing.value = true;
  try {
    report.value = await adminApi().auditAirportSubscription({ subUrl: subUrl.value.trim() });
  } catch (error) {
    showToast(
      "error",
      error instanceof BizError ? error.message : `尽调失败：${(error as Error).message}`,
    );
  } finally {
    auditing.value = false;
  }
}
</script>

<template>
  <Modal title="订阅尽调" :wide="report !== null" @close="emit('close')">
    <div class="admin-form">
      <div v-if="!report" class="admin-field">
        <label for="audit-sub-url">候选机场订阅链接</label>
        <p class="admin-note">
          只读探测，不写库：解析候选机场的节点域名，判断是否与库里已有订阅撞同一个故障域（同一家中转商）。
        </p>
        <input
          id="audit-sub-url"
          v-model="subUrl"
          class="admin-input fact"
          placeholder="https://…?token=…"
          :disabled="auditing"
        />
      </div>

      <template v-else>
        <div class="audit-summary">
          <span
            >机场：<span class="fact">{{ report.airportName ?? "未知" }}</span></span
          >
          <span
            >节点数：<span class="fact">{{ report.totalNodes }}</span></span
          >
          <span
            >协议：<span class="fact">{{ report.protocols.join("、") || "—" }}</span></span
          >
        </div>

        <!-- 全份报告最重要的结论：撞了故障域就等于花两份钱买同一个入口，采购上要一眼看到、否决 -->
        <div v-if="report.conflictsWith.length > 0" class="audit-verdict audit-verdict-danger">
          <strong>与现有订阅同故障域，建议否决这次采购</strong>
          <p>撞车订阅：{{ report.conflictsWith.join("、") }}</p>
        </div>
        <div v-else class="audit-verdict audit-verdict-ok">未发现与现有订阅撞故障域。</div>

        <div class="admin-field">
          <p class="admin-note">
            按节点名匹配 🇺🇸 / [US] / 【US】 等关键词，判定为启发式，请核对——机场命名不规范时会误判，
            不是确定结论。以下
            {{ report.usNodeCount }} 个节点被判定为美国落地：
          </p>
          <ul v-if="report.usNodeNames.length > 0" class="audit-us-list">
            <li v-for="name in report.usNodeNames" :key="name">{{ name }}</li>
          </ul>
          <p v-else class="muted">未发现疑似美国节点。</p>
        </div>

        <div v-if="report.failureDomains.length > 0" class="admin-field">
          <label>故障域分布</label>
          <p class="admin-note">
            故障域是节点域名 CNAME 链的终点，同一故障域下的节点共用一台中转入口机。
          </p>
          <table class="admin-table dense">
            <thead>
              <tr>
                <th>域名</th>
                <th>节点数</th>
                <th>美国节点数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="fd in report.failureDomains" :key="fd.domain">
                <td class="fact">{{ fd.domain }}</td>
                <td>{{ fd.nodeCount }}</td>
                <td>{{ fd.usNodeCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">
        {{ report ? "关闭" : "取消" }}
      </button>
      <button v-if="!report" type="button" class="admin-btn" :disabled="auditing" @click="submit()">
        {{ auditing ? "尽调中…" : "开始尽调" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.audit-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--color-ink-secondary);
}

.audit-verdict {
  padding: 12px 14px;
  border-radius: var(--radius-button);
  font-size: 13px;
  line-height: 1.6;
}

/* 全份报告最重要的结论：故障域撞车＝花两份钱买同一个入口，必须比其它文案更醒目 */
.audit-verdict-danger {
  background: rgba(179, 52, 31, 0.08);
  border: 1px solid var(--counter-danger);
  color: var(--counter-danger-deep);
}

.audit-verdict-danger strong {
  font-size: 14px;
}

.audit-verdict-ok {
  background: var(--color-bg-cloud);
  border: 1px solid var(--color-border);
  color: var(--color-ink-secondary);
}

/* 美国节点名单可能有几十条，限高滚动，别把弹窗撑破 */
.audit-us-list {
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
  padding: 8px 12px;
  list-style: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-button);
  font-family: var(--font-fact);
  font-size: 12px;
}

.audit-us-list li + li {
  margin-top: 4px;
}
</style>
