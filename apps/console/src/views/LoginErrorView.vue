<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import GateShell from "../components/GateShell.vue";

// 重试直接走 signIn：它会重打环路标记再跳登录——重试又失败时守卫据此直接回到本页
const auth = useAuthStore();
</script>

<template>
  <GateShell wide>
    <h1 class="gate-title">登录未能完成</h1>
    <p class="gate-text gate-text-long">
      浏览器在登录页与控制台之间来回跳转，说明登录握手回来后会话没有生效。 常见原因：服务端与 Logto
      的应用配置不匹配、会话签名密钥被更换、或浏览器拒收会话 Cookie。
    </p>
    <p class="gate-text gate-text-long">请稍后重试；仍不行请联系我们，并说明你登录时用的邮箱。</p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="auth.signIn()">重试登录</button>
    </div>
  </GateShell>
</template>
