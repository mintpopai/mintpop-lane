<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";

const props = defineProps<{ subscriptionId: number; workspace: string }>();
const emit = defineEmits<{ closed: [] }>();

const host = ref<HTMLDivElement>();
const error = ref("");
let sessionId: string | undefined;
const unlisteners: UnlistenFn[] = [];
let onResize: (() => void) | undefined;

onMounted(async () => {
  const term = new Terminal({ fontFamily: "Menlo, Consolas, monospace", fontSize: 13 });
  const fit = new FitAddon();
  term.loadAddon(fit);
  term.open(host.value!);
  fit.fit();

  try {
    sessionId = await invoke<string>("open_session", {
      rows: term.rows,
      cols: term.cols,
      subscriptionId: props.subscriptionId,
      workspace: props.workspace,
    });
  } catch (e) {
    // 链路不可用/套餐失效时如实告知，不做任何降级重试
    error.value = String(e);
    return;
  }

  term.onData((data) => invoke("write_session", { id: sessionId, data }));
  unlisteners.push(
    await listen<{ id: string; data: string }>("session://output", (event) => {
      if (event.payload.id === sessionId) term.write(event.payload.data);
    }),
  );
  unlisteners.push(
    await listen<{ id: string }>("session://exit", (event) => {
      // agent 进程退出（用户敲 exit 或崩溃）：清理并回到向导
      if (event.payload.id === sessionId) emit("closed");
    }),
  );

  onResize = () => {
    fit.fit();
    invoke("resize_session", { id: sessionId, rows: term.rows, cols: term.cols });
  };
  window.addEventListener("resize", onResize);
});

onUnmounted(() => {
  unlisteners.forEach((fn) => fn());
  if (onResize) window.removeEventListener("resize", onResize);
  if (sessionId) invoke("close_session", { id: sessionId });
});
</script>

<template>
  <div v-if="error" class="error">
    无法打开终端：{{ error }}
    <button @click="emit('closed')">返回</button>
  </div>
  <div v-else ref="host" class="terminal"></div>
</template>

<style scoped>
.terminal {
  flex: 1;
  width: 100%;
}
.error {
  padding: 24px;
  color: #c00;
  font-family: monospace;
}
</style>
