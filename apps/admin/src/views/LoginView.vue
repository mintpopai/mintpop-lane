<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import GateShell from "../components/GateShell.vue";

const auth = useAuthStore();
</script>

<template>
  <GateShell>
    <h1 class="gate-title">用管理员账号登录</h1>
    <p class="gate-text">登录后可以管理用户、节点池、套餐与企业。</p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="auth.signIn()">登录</button>
    </div>
  </GateShell>
</template>
