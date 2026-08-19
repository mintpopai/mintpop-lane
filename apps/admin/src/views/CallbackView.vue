<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { logtoClient } from "../auth/logto";

const router = useRouter();
const errorMessage = ref("");

onMounted(async () => {
  try {
    await logtoClient().handleSignInCallback(window.location.href);
    await router.replace({ name: "USERS" });
  } catch (error) {
    errorMessage.value = (error as Error).message;
  }
});
</script>

<template>
  <main style="padding: 48px; text-align: center">
    <p v-if="!errorMessage">正在完成登录…</p>
    <template v-else>
      <p>登录失败：{{ errorMessage }}</p>
      <el-button type="primary" @click="router.replace({ name: 'USERS' })">重试</el-button>
    </template>
  </main>
</template>
