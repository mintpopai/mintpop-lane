<script setup lang="ts">
import { ref } from "vue";
import { invoke } from "@tauri-apps/api/core";

const error = ref("");
const pending = ref(false);

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
    <h1>Mintpop 终端</h1>
    <p class="hint">
      用公司账号登录后，终端会自动接入公司链路并配好 Claude，你无需自行登录 Claude。
    </p>
    <button :disabled="pending" @click="login">
      {{ pending ? "正在打开浏览器…" : "用公司账号登录" }}
    </button>
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
