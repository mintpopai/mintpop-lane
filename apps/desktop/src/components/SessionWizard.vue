<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";

interface AgentCredentialView {
  subscriptionId: number;
  name: string;
  agentType: string;
  displayName: string;
  endsAt: string;
}

const emit = defineEmits<{ launch: [payload: { subscriptionId: number; workspace: string }] }>();

const credentials = ref<AgentCredentialView[]>([]);
const error = ref("");
/** 席位拉取是否已有结果；未出结果前不显示「暂无可用订阅」，免得闪一下空态 */
const loaded = ref(false);
const agentType = ref("");
const subscriptionId = ref<number | null>(null);
const workspace = ref("");

/** 最近使用的 workspace，只是路径字符串，非敏感信息，存 localStorage 即可 */
const RECENT_KEY = "lane.recentWorkspaces";
const recent = ref<string[]>(readRecent());

/** localStorage 里的内容可能被手改坏或被别的版本写脏，解析失败一律退回空列表 */
function readRecent(): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(RECENT_KEY) ?? "[]");
    return Array.isArray(parsed) ? parsed.filter((d) => typeof d === "string") : [];
  } catch {
    return [];
  }
}

const agents = computed(() => {
  const seen = new Map<string, string>();
  for (const c of credentials.value) seen.set(c.agentType, c.displayName);
  return [...seen.entries()].map(([type, display]) => ({ type, display }));
});
const options = computed(() => credentials.value.filter((c) => c.agentType === agentType.value));

onMounted(async () => {
  try {
    credentials.value = await invoke<AgentCredentialView[]>("list_agent_credentials");
    // 只有一种 agent 时直接选中；该 agent 只有一条订阅时同样直选
    if (agents.value.length === 1) pickAgent(agents.value[0].type);
  } catch (e) {
    error.value = String(e);
  } finally {
    loaded.value = true;
  }
});

function pickAgent(type: string) {
  agentType.value = type;
  const list = credentials.value.filter((c) => c.agentType === type);
  subscriptionId.value = list.length === 1 ? list[0].subscriptionId : null;
}

async function browse() {
  const dir = await invoke<string | null>("pick_workspace");
  if (dir) workspace.value = dir;
}

function launch() {
  if (subscriptionId.value === null || !workspace.value) return;
  const next = [workspace.value, ...recent.value.filter((d) => d !== workspace.value)].slice(0, 5);
  localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  emit("launch", { subscriptionId: subscriptionId.value, workspace: workspace.value });
}

/** 止期按本地时区展示日期部分（服务端给的是带 Z 的 UTC 绝对时刻串） */
function day(iso: string) {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
</script>

<template>
  <div class="wizard">
    <h2>新建会话</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <p v-if="loaded && !error && !credentials.length" class="empty">
      暂无可用订阅：请购买服务，或升级客户端以支持新的 agent 类型。
    </p>

    <section v-if="credentials.length && (agents.length > 1 || !agentType)">
      <h3>选择 Agent</h3>
      <button
        v-for="a in agents"
        :key="a.type"
        :class="{ picked: a.type === agentType }"
        @click="pickAgent(a.type)"
      >
        {{ a.display }}
      </button>
    </section>

    <section v-if="agentType && options.length > 1">
      <h3>选择套餐</h3>
      <button
        v-for="c in options"
        :key="c.subscriptionId"
        :class="{ picked: c.subscriptionId === subscriptionId }"
        @click="subscriptionId = c.subscriptionId"
      >
        {{ c.name }}（至 {{ day(c.endsAt) }}）
      </button>
    </section>

    <section v-if="subscriptionId !== null">
      <h3>选择 Workspace</h3>
      <div class="row">
        <input v-model="workspace" placeholder="项目目录路径" />
        <button @click="browse">浏览…</button>
      </div>
      <div v-if="recent.length" class="recent">
        <span>最近：</span>
        <button v-for="d in recent" :key="d" class="link" @click="workspace = d">{{ d }}</button>
      </div>
    </section>

    <button
      v-if="credentials.length"
      class="primary"
      :disabled="subscriptionId === null || !workspace"
      @click="launch"
    >
      启动
    </button>
  </div>
</template>

<style scoped>
.wizard {
  max-width: 560px;
  margin: 40px auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
h2 { margin: 0; }
h3 { margin: 0 0 8px; font-size: 14px; color: #555; }
section button { margin: 0 8px 8px 0; padding: 8px 16px; cursor: pointer; }
button.picked { outline: 2px solid #2e7d32; }
.row { display: flex; gap: 8px; }
.row input { flex: 1; padding: 6px 8px; }
.recent { font-size: 12px; color: #777; }
.recent .link { border: none; background: none; color: #1565c0; cursor: pointer; padding: 0 6px; }
.primary { align-self: flex-start; padding: 10px 28px; }
.error { color: #c00; }
.empty { margin: 0; color: #777; line-height: 1.8; }
</style>
