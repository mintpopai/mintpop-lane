<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import TerminalView from "./components/Terminal.vue";

type LinkState = "DISCONNECTED" | "CONNECTING" | "ACTIVE" | "DEGRADED" | "REVOKED";

const status = ref<LinkState>("DISCONNECTED");
let timer: ReturnType<typeof setInterval> | undefined;

const label: Record<LinkState, string> = {
  DISCONNECTED: "链路未连接",
  CONNECTING: "正在连接公司链路…",
  ACTIVE: "已接入公司链路",
  DEGRADED: "链路异常，暂不可用",
  REVOKED: "账号已被停用",
};

onMounted(() => {
  const poll = async () => {
    status.value = await invoke<LinkState>("link_status");
  };
  poll();
  timer = setInterval(poll, 3000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="app">
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
