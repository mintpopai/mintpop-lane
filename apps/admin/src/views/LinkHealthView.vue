<script setup lang="ts">
// 链路健康页：三期唯一一个人真正会打开的页面，前面几期的上报/告警/归档都是为了这里的数字。
//
// 硬规则（spec §8.3，不自行发挥）：
// - 只按「故障域 × 运营商」展示，不按节点展示——同一故障域下的节点共用一台中转入口机，
//   不是独立样本，按节点画会让人以为「换个节点就好了」。
// - successRate 为 null 显示成「无数据」，不能画成 0% 或空白：空白会被读成「正常」，
//   0% 会被读成「全挂」，两个方向都错。
// - failureDomain 为空串显示成「未解析」并带视觉提示：故障域未解析意味着这组节点的冗余情况
//   根本无从判断，比「只有一个故障域」更糟（与用户详情页「入口无冗余」同一口径）。
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import { DNS_VANTAGE_LABELS } from "../api/types";
import type { LinkHealthDomainRow, LinkHealthResponse } from "../api/types";
import AdminSelect from "../components/AdminSelect.vue";
import DataCard from "../components/DataCard.vue";
import PageHead from "../components/PageHead.vue";
import { formatDateTime } from "../utils/format";

const health = ref<LinkHealthResponse | null>(null);
const loading = ref(true);
const loadError = ref("");
const days = ref(7);

const dayOptions = [
  { value: 7, label: "近 7 天" },
  { value: 30, label: "近 30 天" },
  { value: 90, label: "近 90 天" },
];

/** 一个故障域下全部运营商之和算出的域级成功率；样本为 0 时同样是「无数据」而不是 0% */
function domainRate(domain: LinkHealthDomainRow): number | null {
  return domain.samples > 0 ? domain.aliveCount / domain.samples : null;
}

/** 成功率统一显示成一位小数的百分比；null 一律交给调用处显示成「无数据」，这里不兜底成 0% */
function formatRate(rate: number | null): string {
  return rate === null ? "无数据" : `${(rate * 100).toFixed(1)}%`;
}

/**
 * 成功率低于这个值就标红。取值刻意与服务端 `link-report.alert-threshold` 的默认值同一条线：
 * 页面上标红的格子，正是服务端会推飞书告警的那些，两边口径一致，人不用在心里再换算一次。
 * 服务端那个值可配置，这里是写死的常量——真调整了阈值，记得两边一起改。
 */
const ALERT_THRESHOLD = 0.8;

/**
 * 成功率是否已经低到会触发告警。null（无样本）不算——「没有数据」不是「跌破阈值」，
 * 把它也标红会让人以为出了故障，实际只是这段时间没人从那个运营商上来。
 */
function isDegraded(rate: number | null): boolean {
  return rate !== null && rate < ALERT_THRESHOLD;
}

function ispLabel(isp: string): string {
  return isp === "" ? "未知运营商" : isp;
}

function vantageLabel(vantage: string): string {
  return DNS_VANTAGE_LABELS[vantage] ?? vantage;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    health.value = await adminApi().getLinkHealth(days.value);
    loadError.value = "";
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

function onDaysChange(value: number): void {
  days.value = value;
  load();
}

const domains = computed(() => health.value?.domains ?? []);
const timeline = computed(() => health.value?.entryIpTimeline ?? []);

onMounted(load);
</script>

<template>
  <PageHead title="链路健康">
    <template #facts>
      故障域 × 运营商的成功率矩阵，覆盖全部用户；不按节点展示——同一故障域下的节点共用一台
      中转入口机，不是独立样本。
    </template>
    <template #actions>
      <AdminSelect
        v-model="days"
        prefix="范围"
        mono
        :options="dayOptions"
        aria-label="回看天数"
        @update:model-value="(v) => onDaysChange(v as number)"
      />
    </template>
  </PageHead>

  <DataCard
    :loading="loading"
    :error="loadError"
    :empty="domains.length === 0"
    empty-text="这段时间还没有链路上报数据。"
  >
    <ul class="domain-list">
      <li
        v-for="domain in domains"
        :key="domain.failureDomain"
        class="domain-group"
        :class="{ 'domain-unresolved': domain.failureDomain === '' }"
      >
        <header class="domain-header">
          <span class="domain-name">
            <span v-if="domain.failureDomain === ''" class="domain-badge-unresolved">
              ⚠ 未解析
            </span>
            <span v-else class="fact">{{ domain.failureDomain }}</span>
          </span>
          <span class="domain-stats muted">
            成功率
            <span :class="{ 'cell-degraded': isDegraded(domainRate(domain)) }">{{
              formatRate(domainRate(domain))
            }}</span>
            · 样本 {{ domain.samples }} · 故障切换 {{ domain.failovers }} 次
          </span>
        </header>

        <p v-if="domain.failureDomain === ''" class="domain-unresolved-warning">
          未解析：这组节点尚未解析出故障域，暂无法确认是否共用同一台中转入口机——按最坏情况
          处理，视同没有冗余，比「只有一个故障域」更糟，需要尽快让节点解析出故障域。
        </p>

        <table class="admin-table">
          <thead>
            <tr>
              <th>运营商</th>
              <th>样本数</th>
              <th>存活数</th>
              <th>成功率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cell in domain.isps" :key="cell.isp">
              <td>{{ ispLabel(cell.isp) }}</td>
              <td class="fact">{{ cell.samples }}</td>
              <td class="fact">{{ cell.aliveCount }}</td>
              <td
                class="fact"
                :class="{
                  'cell-no-data': cell.successRate === null,
                  'cell-degraded': isDegraded(cell.successRate),
                }"
              >
                {{ formatRate(cell.successRate) }}
              </td>
            </tr>
          </tbody>
        </table>
      </li>
    </ul>
  </DataCard>

  <div class="section-head">
    <h3 class="block-title">入口 IP 变更时间线</h3>
  </div>
  <div class="admin-card timeline-card">
    <p v-if="timeline.length === 0" class="card-state">暂无入口 IP 变更记录。</p>
    <table v-else class="admin-table">
      <thead>
        <tr>
          <th>故障域</th>
          <th>视角</th>
          <th>变更前</th>
          <th>变更后</th>
          <th>变更时刻</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(change, index) in timeline" :key="`${change.failureDomain}-${index}`">
          <td>{{ change.failureDomain === "" ? "未解析" : change.failureDomain }}</td>
          <td>{{ vantageLabel(change.vantage) }}</td>
          <td class="fact muted">{{ change.previousIps }}</td>
          <td class="fact">{{ change.currentIps }}</td>
          <td class="fact muted">{{ formatDateTime(change.changedAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.domain-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.domain-group {
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

/* 未解析的故障域比「只有一个故障域」更糟，边框与背景要一眼和正常组区分开，
   与用户详情页「入口无冗余」同一套需要停下来看的琥珀色语义 */
.domain-group.domain-unresolved {
  border-color: color-mix(in srgb, #b4720b 45%, var(--color-border));
  background: color-mix(in srgb, #b4720b 6%, #ffffff);
}

.domain-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.domain-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.domain-badge-unresolved {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #b4720b;
  background: color-mix(in srgb, #b4720b 16%, #ffffff);
}

.domain-stats {
  font-size: 12px;
}

.domain-unresolved-warning {
  margin: 0 0 12px;
  padding: 10px 14px;
  border-radius: var(--radius-card);
  border: 1px solid color-mix(in srgb, #b4720b 35%, var(--color-border));
  background: color-mix(in srgb, #b4720b 10%, #ffffff);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-ink);
}

/*
 * 跌破告警阈值的成功率标红加粗。这个页面的用途是「一眼看出哪里不对」，
 * 而在加这条之前，40.0% 与 96.4% 的颜色、字重、背景完全相同，得逐个读数字才看得出问题。
 * 不靠颜色单独传达：数字本身仍然完整显示，色只是让扫视时先落到它身上。
 */
.cell-degraded {
  color: #b42318;
  font-weight: 600;
}

/* 无数据的格子弱化显示，但不是靠颜色单独传达——文案本身已经说清「无数据」与「0%」不同 */
.cell-no-data {
  color: var(--color-ink-secondary);
  font-style: italic;
}

.timeline-card {
  padding: 4px 0;
}

.timeline-card .card-state {
  padding: 24px;
  color: var(--color-ink-secondary);
  font-size: 14px;
}
</style>
