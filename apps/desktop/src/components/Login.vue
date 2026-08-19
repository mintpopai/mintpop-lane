<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

const error = ref("");
const pending = ref(false);
/** 引导配置是否已就绪。没就绪时登录按钮点了也没用，直接换成重试按钮 */
const configReady = ref(false);
const configError = ref("");
const retrying = ref(false);
const unlisteners: UnlistenFn[] = [];

onMounted(async () => {
  // 引导可能在本组件挂载之前就已完成，光监听事件会漏掉，故先查一次当前状态
  configReady.value = await invoke<boolean>("client_config_ready");

  unlisteners.push(
    await listen("auth://config-ready", () => {
      configReady.value = true;
      configError.value = "";
    }),
  );
  unlisteners.push(
    await listen<{ reason: string }>("auth://config-failed", (event) => {
      configReady.value = false;
      configError.value = event.payload.reason;
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

async function retry() {
  retrying.value = true;
  try {
    await invoke("reload_client_config");
  } catch (e) {
    configError.value = String(e);
  } finally {
    retrying.value = false;
  }
}
</script>

<template>
  <div class="login">
    <h1>Mintpop 终端</h1>
    <p class="hint">
      用公司账号登录后，终端会自动接入公司链路并配好 Claude，你无需自行登录 Claude。
    </p>

    <template v-if="configReady">
      <button :disabled="pending" @click="login">
        {{ pending ? "正在打开浏览器…" : "用公司账号登录" }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </template>

    <template v-else>
      <p class="error">无法连接服务端，暂时不能登录。</p>
      <p v-if="configError" class="error">{{ configError }}</p>
      <button :disabled="retrying" @click="retry">
        {{ retrying ? "正在重试…" : "重试" }}
      </button>
    </template>
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
