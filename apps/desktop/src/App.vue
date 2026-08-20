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
interface MeData {
  email: string;
  name: string;
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
  CONNECTING: "正在连接公司链路…",
  ACTIVE: "已接入公司链路",
  DEGRADED: "链路异常，暂不可用",
  EXPIRED: "服务已到期，请续费后重连",
  REVOKED: "账号已被停用",
};

async function poll() {
  loggedIn.value = await invoke<boolean>("auth_status");
  status.value = await invoke<LinkState>("link_status");
  notice.value = await invoke<LinkNotice | null>("link_notice");
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
      <span v-if="me" class="who">{{ me.name }}（{{ me.email }}）</span>
    </header>

    <template v-if="status === 'ACTIVE'">
      <TerminalView
        v-if="session"
        :subscription-id="session.subscriptionId"
        :workspace="session.workspace"
        @closed="session = null"
      />
      <SessionWizard v-else @launch="session = $event" />
    </template>

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
        链路不可用时无法启动 Agent。请勿绕过本终端直接使用——从非受控链路访问会导致账号被风控封禁。
      </p>
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
.who {
  float: right;
  opacity: 0.8;
}
.blocked {
  padding: 32px;
  line-height: 1.8;
  color: #444;
}
</style>
