<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import GateShell from "../components/GateShell.vue";

const auth = useAuthStore();
</script>

<template>
  <GateShell>
    <h1 class="gate-title">请先登录</h1>
    <p class="gate-text">
      后台的所有页面都需要管理员身份才能访问。点击下方按钮前往统一登录（Logto）完成认证，
      登录成功后会自动回到这里。
    </p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="auth.signIn()">登录</button>
    </div>
  </GateShell>
</template>
