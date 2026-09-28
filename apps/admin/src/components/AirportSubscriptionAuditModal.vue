<script setup lang="ts">
// 采购尽调：给一个候选机场的试用订阅链接，判断它是否与库里已有节点撞故障域。
// 全程只读、不落库，与「重新拉取」「导入」这类会改订阅数据的弹窗不同，故不 emit saved，
// 不需要父级 load() 刷新任何东西。
import { ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { SubAuditFailureDomainReport, SubAuditResponse } from "../api/types";
import { showToast } from "../toast";
import { booleanLabel } from "../utils/format";
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

/**
 * 把一个故障域的各视角入口 IP 与 ASN 拉平成可渲染的行。
 * 视角名（CHINA_TELECOM 等）是服务端 DnsVantage 的枚举取值，前端不做映射、原样罗列——
 * 与 types.ts 上「不额外镜像该枚举」的约定保持一致。
 * entryIps 为 null（服务端未查询该故障域）时返回空数组，由模板走「未查询」那一支。
 */
function vantageRows(
  fd: SubAuditFailureDomainReport,
): { vantage: string; ips: string; asns: string }[] {
  if (!fd.entryIps) {
    return [];
  }
  return Object.entries(fd.entryIps).map(([vantage, ips]) => ({
    vantage,
    // 该视角解析失败时服务端给的就是空列表，说清楚「解析为空」，别渲染成空白
    ips: ips.length > 0 ? ips.join("、") : "解析为空",
    asns: (fd.asns?.[vantage] ?? []).join("、") || "ASN 未知",
  }));
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
            入口 IP 与 ASN 只对判定为美国落地的故障域查（LAND 的国家级 GeoIP
            限制决定了前置只能选美国落地的节点）， 其余故障域标「未查询」。采购标准里「入口 ASN ≠
            AS16509（现有是 AWS 东京）」就看这一列。
          </p>
          <table class="admin-table dense">
            <thead>
              <tr>
                <th>域名</th>
                <th>节点数</th>
                <th>美国节点数</th>
                <th class="audit-entry-col">入口 IP / ASN（按视角）</th>
                <th>分线路</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="fd in report.failureDomains" :key="fd.domain">
                <td class="fact">{{ fd.domain }}</td>
                <td>{{ fd.nodeCount }}</td>
                <td>{{ fd.usNodeCount }}</td>
                <td class="audit-entry-col">
                  <ul v-if="fd.entryIps" class="audit-vantage-list">
                    <li v-for="row in vantageRows(fd)" :key="row.vantage">
                      <span class="audit-vantage-name">{{ row.vantage }}</span>
                      <span>
                        <span class="fact">{{ row.ips }}</span>
                        <span class="muted"> · {{ row.asns }}</span>
                      </span>
                    </li>
                  </ul>
                  <!-- 未查询 ≠ 查了没结果：必须说清楚，否则会被读成「这个故障域没入口 IP」 -->
                  <span v-else class="muted">未查询入口 IP</span>
                </td>
                <td>
                  <span v-if="fd.lineSplit === null" class="muted">未查询</span>
                  <span v-else>{{ booleanLabel(fd.lineSplit, "是", "否") }}</span>
                </td>
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

/* 这一列必须钉死宽度：全局 .admin-table tr > *:last-child 会把富余宽度全给最后一列，
   本列不给定宽就会被压到只剩几个字符宽，IP 逐字竖排。定宽 + 允许换行，
   一个视角解析到两个 IP（jp.tsdns.top 实测就是两个 AWS 东京 IP 轮询）时在格子里折行，
   不会横向撑出去压到右边的「分线路」列上 */
.audit-entry-col {
  width: 340px;
  min-width: 340px;
}

/* 一个故障域四个视角，塞在同一格里逐行列出，别把表撑成四倍行数 */
.audit-vantage-list {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 12px;
}

/* 两列网格而不是 flex-wrap：窄屏下 flex 换行会让 ASN 掉到下一行、
   与下一个视角名并排，读起来像是「AS15169 属于 CHINA_UNICOM」。
   网格把「视角名」与「IP · ASN」钉在各自的列里，换行只发生在值那一列内部 */
.audit-vantage-list li {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 2px 10px;
  align-items: baseline;
}

.audit-vantage-list li + li {
  margin-top: 4px;
}

.audit-vantage-name {
  color: var(--color-ink-secondary);
}

/* 全局 .admin-table td 是 white-space: nowrap（邮箱、Logto id 这类长 ASCII 串没有断点，
   靠卡片自身横向滚动兜底），这一格例外：它是我们自己排的多行列表、断点明确。
   只放开换行、不用 overflow-wrap: anywhere——后者会让 IP 从中间断开，读起来更糟 */
.audit-vantage-list li > span:last-child {
  white-space: normal;
}
</style>
