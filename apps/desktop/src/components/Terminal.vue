<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { Terminal } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import "@xterm/xterm/css/xterm.css";

const host = ref<HTMLDivElement>();
const error = ref("");
let unlisten: UnlistenFn | undefined;
let onResize: (() => void) | undefined;

onMounted(async () => {
  const term = new Terminal({
    fontFamily: "Menlo, Consolas, monospace",
    fontSize: 13,
  });
  const fit = new FitAddon();
  term.loadAddon(fit);
  term.open(host.value!);
  fit.fit();

  let id: string;
  try {
    id = await invoke<string>("open_session", { rows: term.rows, cols: term.cols });
  } catch (e) {
    // 链路不可用时如实告知，不做任何降级重试
    error.value = String(e);
    return;
  }

  term.onData((data) => invoke("write_session", { id, data }));
  unlisten = await listen<{ id: string; data: string }>("session://output", (event) => {
    if (event.payload.id === id) term.write(event.payload.data);
  });

  onResize = () => {
    fit.fit();
    invoke("resize_session", { id, rows: term.rows, cols: term.cols });
  };
  window.addEventListener("resize", onResize);
});

onUnmounted(() => {
  unlisten?.();
  if (onResize) window.removeEventListener("resize", onResize);
});
</script>

<template>
  <div v-if="error" class="error">无法打开终端：{{ error }}</div>
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
