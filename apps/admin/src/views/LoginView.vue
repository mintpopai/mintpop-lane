<script setup lang="ts">
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
</script>

<template>
  <div class="gate">
    <div class="gate-box">
      <p class="gate-brand"><span class="wordmark">MintPop</span> Lane 管理后台</p>
      <h1 class="gate-title">请先登录</h1>
      <p class="gate-text">
        后台的所有页面都需要管理员身份才能访问。点击下方按钮前往统一登录（Logto）完成认证，
        登录成功后会自动回到这里。
      </p>
      <div class="gate-actions">
        <button type="button" class="gate-btn" @click="auth.signIn()">登录</button>
      </div>
    </div>
  </div>
</template>
