<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

type ConfigPhase = "UNKNOWN" | "READY" | "FAILED";
/** client_config_state 的返回形状：与 Rust 侧 BootstrapState 的 adjacently-tagged 序列化逐字对应 */
type BootstrapState =
  | { phase: "UNKNOWN" }
  | { phase: "READY" }
  | { phase: "FAILED"; reason: string };

const error = ref("");
const pending = ref(false);
/** 引导配置的当前阶段。UNKNOWN 是"还没听到结果"的中性态，不代表失败 */
const configPhase = ref<ConfigPhase>("UNKNOWN");
const configError = ref("");
const retrying = ref(false);
const unlisteners: UnlistenFn[] = [];

onMounted(async () => {
  // 先监听再补查，否则两次 await 之间到达的事件会丢失且不会重放：
  // 若先 await invoke("client_config_state") 再 listen，引导恰好在这两次
  // await 之间完成或失败时，对应事件会在没有监听者的情况下发出，
  // Tauri 不会重放事件，页面就永久卡在中性态（且拉不到重试所需的失败原因）。
  // 故必须先挂好监听，再用 invoke 补查一次"挂载前引导是否已经有结果"。
  unlisteners.push(
    await listen("auth://config-ready", () => {
      configPhase.value = "READY";
      configError.value = "";
    }),
  );
  unlisteners.push(
    await listen<{ reason: string }>("auth://config-failed", (event) => {
      configPhase.value = "FAILED";
      configError.value = event.payload.reason;
    }),
  );

  // 查询在飞行期间也可能有事件先到达并把 configPhase 改掉（READY 或 FAILED），
  // 事件的结论至少和查询一样新，因此只在查询回来时 phase 仍是 UNKNOWN 才采纳它，
  // 绝不用查询结果覆盖已经落地的事件结论。
  const state = await invoke<BootstrapState>("client_config_state");
  if (configPhase.value !== "UNKNOWN") {
    return;
  }
  if (state.phase === "READY") {
    configPhase.value = "READY";
  } else if (state.phase === "FAILED") {
    configPhase.value = "FAILED";
    configError.value = state.reason;
  }
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
  configPhase.value = "UNKNOWN";
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
    <h1>Pier</h1>
    <p class="hint">
      用公司账号登录后，终端会自动接入公司链路并配好 Claude，你无需自行登录 Claude。
    </p>

    <template v-if="configPhase === 'READY'">
      <button :disabled="pending" @click="login">
        {{ pending ? "正在打开浏览器…" : "用公司账号登录" }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </template>

    <template v-else-if="configPhase === 'UNKNOWN'">
      <p class="hint">正在连接服务端…</p>
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
