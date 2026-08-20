<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

/** 被强制登出的原因，由常驻挂载的 App.vue 接住 auth://changed 事件后传入，
 *  避免登录页尚未挂载时事件被 Tauri 静默丢弃（不重放）。 */
defineProps<{ notice?: string }>();

const error = ref("");
const pending = ref(false);
const unlisteners: UnlistenFn[] = [];

onMounted(async () => {
  unlisteners.push(
    await listen<{ reason: string }>("auth://login-failed", (event) => {
      error.value = event.payload.reason;
    }),
  );
});

onUnmounted(() => {
  unlisteners.forEach((fn) => fn());
});

async function login() {
  pending.value = true;
  error.value = "";
  try {
    await invoke("start_login");
  } catch (e) {
    error.value = String(e);
  } finally {
    pending.value = false;
  }
}
</script>

<template>
  <div class="login">
    <h1>Lane</h1>
    <p class="hint">
      登录后终端会自动接入专属链路并配好你购买的 Agent 服务；首次使用会自动完成注册。
    </p>

    <button :disabled="pending" @click="login">
      {{ pending ? "正在打开浏览器…" : "登录 / 注册" }}
    </button>
    <p v-if="notice" class="error">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;
}
h1 {
  margin: 0;
  font-size: 22px;
}
.hint {
  max-width: 420px;
  margin: 0;
  text-align: center;
  color: #666;
  line-height: 1.7;
}
button {
  padding: 10px 24px;
  font-size: 14px;
  cursor: pointer;
}
button:disabled {
  cursor: default;
  opacity: 0.6;
}
.error {
  color: #c00;
}
</style>
