<script setup lang="ts">
// 落地节点连通性检测：打开即经该节点探测一次实际出口 IP，与登记值比对；
// 登记缺失或不一致时可一键把实际 IP 填回节点（走整体更新接口，密码传空即沿用原值）
import { onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AdminNodeResponse, NodeProbeResponse } from "../api/types";
import { showToast } from "../toast";
import { lookupIpTimezone } from "../utils/ipTimezone";
import { buildNodePayload, nodeToForm } from "../utils/nodeForm";
import Modal from "./AdminModal.vue";

const props = defineProps<{ node: AdminNodeResponse }>();
const emit = defineEmits<{ close: []; saved: [] }>();

const probing = ref(false);
const result = ref<NodeProbeResponse | null>(null);
const filling = ref(false);

async function probe(): Promise<void> {
  probing.value = true;
  result.value = null;
  try {
    result.value = await adminApi().probeNode(props.node.id);
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : "检测请求失败，请稍后重试");
  } finally {
    probing.value = false;
  }
}

onMounted(probe);

/** 结论一句话：不通 / 未登记 / 一致 / 不一致，页面上只认这四种 */
function verdictOf(r: NodeProbeResponse): { state: string; text: string } {
  if (!r.reachable) {
    return { state: "REVOKED", text: "不通" };
  }
  if (r.matched === null) {
    return { state: "MISSING", text: "未登记出口 IP" };
  }
  return r.matched ? { state: "CONFIGURED", text: "与登记一致" } : { state: "REVOKED", text: "与登记不一致" };
}

/** 连通且实际 IP 与登记不一致（含未登记）才有「填入」的必要 */
function canFill(r: NodeProbeResponse | null): r is NodeProbeResponse & { actualEgressIp: string } {
  return r !== null && r.reachable && r.actualEgressIp !== null && r.matched !== true;
}

async function fillEgressIp(): Promise<void> {
  if (!canFill(result.value)) {
    return;
  }
  const actualIp = result.value.actualEgressIp;
  filling.value = true;
  try {
    // 复用表单的「行 → 请求」组装：密码留空即沿用原值，其余字段原样提交
    const payload = buildNodePayload(nodeToForm(props.node));
    payload.egressIp = actualIp;
    // 出口 IP 变了时区要跟着变：按新 IP 查 GeoIP 覆盖时区（与表单改 IP 时的联动、服务端巡检回填一致）；
    // 查不到就保留原时区，管理员可事后在表单里改
    payload.egressTimezone = (await lookupIpTimezone(actualIp)) ?? payload.egressTimezone;
    await adminApi().updateNode(props.node.id, payload);
    showToast("success", `已把出口 IP 填为 ${actualIp}`);
    emit("saved");
    emit("close");
  } catch (error) {
    showToast("error", error instanceof BizError ? error.message : "保存失败，请稍后重试");
  } finally {
    filling.value = false;
  }
}
</script>

<template>
  <Modal :title="`检测节点：${node.name}`" @close="emit('close')">
    <div class="probe-body">
      <p class="admin-note">
        由服务端经 <span class="fact">{{ node.serverAddr }}:{{ node.port }}</span> 出站访问公网 IP 回显服务，
        看这条落地代理是否连通、实际出口是哪个 IP。
      </p>

      <p v-if="probing" class="probe-pending">检测中，最长约 45 秒…</p>

      <template v-else-if="result">
        <dl class="probe-facts">
          <div class="probe-fact">
            <dt>结论</dt>
            <dd>
              <span id="probe-verdict" class="state" :data-state="verdictOf(result).state">
                {{ verdictOf(result).text }}
              </span>
              <span class="fact muted">{{ result.latencyMs }} ms</span>
            </dd>
          </div>
          <div class="probe-fact">
            <dt>实际出口 IP</dt>
            <dd id="probe-actual-ip" class="fact">{{ result.actualEgressIp ?? "—" }}</dd>
          </div>
          <div class="probe-fact">
            <dt>登记出口 IP</dt>
            <dd id="probe-registered-ip" class="fact">{{ result.registeredEgressIp ?? "—" }}</dd>
          </div>
          <div v-if="result.error" class="probe-fact">
            <dt>失败原因</dt>
            <dd class="probe-error">{{ result.error }}</dd>
          </div>
        </dl>
        <p v-if="canFill(result)" class="probe-hint">
          填入会把节点的出口 IP 改为实际探测值{{ node.egressTimezone ? "" : "，并按 GeoIP 预填出口时区" }}；
          其它字段与密码保持不变。
        </p>
      </template>

      <p v-else class="probe-pending muted">检测请求未完成，可重新检测。</p>
    </div>
    <template #footer>
      <button type="button" class="admin-btn-ghost" :disabled="filling" @click="emit('close')">关闭</button>
      <button id="probe-retry" type="button" class="admin-btn-ghost" :disabled="probing || filling" @click="probe()">
        {{ probing ? "检测中…" : "重新检测" }}
      </button>
      <button v-if="canFill(result)" type="button" class="admin-btn" :disabled="filling" @click="fillEgressIp()">
        {{ filling ? "保存中…" : `填入出口 IP ${result?.actualEgressIp}` }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.probe-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.probe-pending {
  font-size: 14px;
  color: var(--color-ink);
}

.probe-facts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.probe-fact {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.probe-fact dt {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.probe-fact dd {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-ink);
}

.probe-error {
  word-break: break-all;
  color: #b3341f;
}

.probe-hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-ink-secondary);
}
</style>
