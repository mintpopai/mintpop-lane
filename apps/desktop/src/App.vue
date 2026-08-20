<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import LoginView from "./components/Login.vue";
import TerminalView from "./components/Terminal.vue";
import SessionWizard from "./components/SessionWizard.vue";

type LinkState = "DISCONNECTED" | "CONNECTING" | "ACTIVE" | "DEGRADED" | "EXPIRED" | "REVOKED";
interface LinkNotice {
  code: number;
  msg: string;
}
interface MeSubscription {
  id: number;
  name: string;
  agentType: string;
  endsAt: string;
  active: boolean;
}
interface MeData {
  email: string;
  name: string;
  subscriptions: MeSubscription[];
}

const status = ref<LinkState>("DISCONNECTED");
const loggedIn = ref(false);
/** 被强制登出的原因（会话过期/账号停用等）。App.vue 常驻挂载，接住 Login.vue
 *  尚未挂载时就可能发出的 auth://changed 事件，再以 prop 传给登录页展示。 */
const logoutReason = ref("");
const notice = ref<LinkNotice | null>(null);
const me = ref<MeData | null>(null);
/** 当前会话参数；null = 显示向导 */
const session = ref<{ subscriptionId: number; workspace: string } | null>(null);
const reconnecting = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
let unlisten: UnlistenFn | undefined;

const label: Record<LinkState, string> = {
  DISCONNECTED: "链路未连接",
  CONNECTING: "正在连接专属链路…",
  ACTIVE: "已接入专属链路",
  DEGRADED: "链路异常，暂不可用",
  EXPIRED: "服务已到期，请续费后重连",
  REVOKED: "账号已被停用",
};

async function poll() {
  // 轮询失败（后端忙/窗口正在关）不该把界面打回未登录态，静默保留上一次的值即可
  try {
    loggedIn.value = await invoke<boolean>("auth_status");
    status.value = await invoke<LinkState>("link_status");
    notice.value = await invoke<LinkNotice | null>("link_notice");
  } catch {
    /* 保留上次状态，等下一轮 */
  }
}

async function loadMe() {
  try {
    me.value = await invoke<MeData>("me_info");
  } catch {
    me.value = null;
  }
}

async function reconnect() {
  reconnecting.value = true;
  try {
    await invoke("reconnect_link");
    await poll();
  } finally {
    reconnecting.value = false;
  }
}

/** 退出登录。会话由后端逐个 kill，终端也会随 loggedIn 变 false 整体卸载 */
async function doLogout() {
  try {
    await invoke("logout");
  } catch {
    /* 后端已尽力清理，失败只影响本次提示，不阻断回登录页 */
  }
  await poll();
}

/** 止期按本地时区展示日期部分（服务端给的是带 Z 的 UTC 绝对时刻串） */
function day(iso: string) {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

onMounted(async () => {
  await poll();
  if (loggedIn.value) await loadMe();
  timer = setInterval(poll, 3000);
  unlisten = await listen<{ logged_in: boolean; reason?: string }>("auth://changed", (event) => {
    loggedIn.value = event.payload.logged_in;
    if (!event.payload.logged_in) {
      logoutReason.value = event.payload.reason ?? "";
      me.value = null;
      session.value = null;
    } else {
      logoutReason.value = "";
      loadMe();
    }
  });
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  unlisten?.();
});
</script>

<template>
  <LoginView v-if="!loggedIn" :notice="logoutReason" />
  <div v-else class="app">
    <header :class="['bar', status]">
      {{ label[status] }}
      <span class="right">
        <span v-if="me" class="who">{{ me.name }}（{{ me.email }}）</span>
        <button class="logout" @click="doLogout">退出登录</button>
      </span>
    </header>

    <!-- 有会话就一直挂着终端：链路抖一下只加一条横幅，绝不卸载。
         卸载会连带 close_session 杀掉用户正在跑的 agent，工作就白做了。 -->
    <template v-if="session">
      <div v-if="status !== 'ACTIVE'" class="thin-bar">
        {{ label[status] }}
        <button :disabled="reconnecting" @click="reconnect">
          {{ reconnecting ? "正在重连…" : "重新连接" }}
        </button>
      </div>
      <TerminalView
        :subscription-id="session.subscriptionId"
        :workspace="session.workspace"
        @closed="session = null"
      />
    </template>

    <SessionWizard v-else-if="status === 'ACTIVE'" @launch="session = $event" />

    <div v-else class="blocked">
      <!-- 未购买/已到期给引导文案，其余给通用提示；notice 的文案来自服务端业务码 -->
      <p v-if="notice && notice.code === 310005">
        你还没有购买任何服务。请联系管理员购买后，点击下方重连。
      </p>
      <p v-else-if="notice && (notice.code === 310006 || status === 'EXPIRED')">
        服务已到期，请续费后点击下方重连。
      </p>
      <p v-else-if="notice">{{ notice.msg }}</p>
      <p v-else>
        链路不可用时无法启动 Agent。请勿绕过本终端从未受控网络访问，那会导致账号被风控封禁。
      </p>

      <table v-if="me && me.subscriptions.length" class="subs">
        <thead>
          <tr>
            <th>套餐</th>
            <th>Agent</th>
            <th>止期</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in me.subscriptions" :key="s.id">
            <td>{{ s.name }}</td>
            <td>{{ s.agentType }}</td>
            <td>{{ day(s.endsAt) }}</td>
            <td :class="s.active ? 'ok' : 'bad'">{{ s.active ? "有效" : "已过期" }}</td>
          </tr>
        </tbody>
      </table>

      <button :disabled="reconnecting" @click="reconnect">
        {{ reconnecting ? "正在重连…" : "重新连接" }}
      </button>
    </div>
  </div>
</template>

<style>
body {
  margin: 0;
}
</style>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.bar {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  font-size: 12px;
  color: #fff;
  background: #888;
}
.bar.ACTIVE {
  background: #2e7d32;
}
.bar.CONNECTING {
  background: #f9a825;
}
.bar.DEGRADED,
.bar.EXPIRED,
.bar.REVOKED,
.bar.DISCONNECTED {
  background: #c62828;
}
.right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.who {
  opacity: 0.8;
}
.logout {
  padding: 2px 10px;
  font-size: 12px;
  color: inherit;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 3px;
  cursor: pointer;
}
.thin-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 12px;
  font-size: 12px;
  color: #7f4f00;
  background: #fff3cd;
  border-bottom: 1px solid #ffe08a;
}
.blocked {
  padding: 32px;
  line-height: 1.8;
  color: #444;
}
.subs {
  margin: 16px 0;
  font-size: 13px;
  border-collapse: collapse;
}
.subs th,
.subs td {
  padding: 4px 16px 4px 0;
  text-align: left;
  font-weight: normal;
}
.subs th {
  color: #888;
}
.subs .ok {
  color: #2e7d32;
}
.subs .bad {
  color: #c62828;
}
</style>
