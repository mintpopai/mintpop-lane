<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import LoginView from "./components/Login.vue";
import TerminalView from "./components/Terminal.vue";

type LinkState = "DISCONNECTED" | "CONNECTING" | "ACTIVE" | "DEGRADED" | "EXPIRED" | "REVOKED";

const status = ref<LinkState>("DISCONNECTED");
const loggedIn = ref(false);
/** 被强制登出的原因（会话过期/账号停用等）。App.vue 常驻挂载，接住 Login.vue
 *  尚未挂载时就可能发出的 auth://changed 事件，再以 prop 传给登录页展示。 */
const logoutReason = ref("");
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
}

onMounted(async () => {
  await poll();
  timer = setInterval(poll, 3000);
  unlisten = await listen<{ logged_in: boolean; reason?: string }>("auth://changed", (event) => {
    loggedIn.value = event.payload.logged_in;
    if (!event.payload.logged_in) {
      logoutReason.value = event.payload.reason ?? "";
    } else {
      logoutReason.value = "";
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
    <header :class="['bar', status]">{{ label[status] }}</header>
    <TerminalView v-if="status === 'ACTIVE'" />
    <div v-else class="blocked">
      链路不可用时无法启动 Agent。请联系管理员，不要绕过本终端使用 Claude——
      从非公司链路访问会导致账号被风控封禁。
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
.blocked {
  padding: 32px;
  line-height: 1.8;
  color: #444;
}
</style>
