<script setup lang="ts">
import { useAuthStore } from "../stores/auth";

// 重试直接走 signIn：它会重打环路标记再跳登录——重试又失败时守卫据此直接回到本页
const auth = useAuthStore();
</script>

<template>
  <div class="gate">
    <div class="gate-box">
      <p class="gate-brand"><span class="wordmark">MintPop</span> Lane 管控后台</p>
      <h1 class="gate-title">登录未能完成</h1>
      <p class="gate-text">
        浏览器在登录页与管控后台之间来回跳转，说明登录握手回来后会话没有生效。
        常见原因：服务端与 Logto 的应用配置不匹配、会话签名密钥被更换、
        或浏览器拒收会话 Cookie（例如未走 HTTPS、管理端与接口不同源）。
      </p>
      <p class="gate-text">请先确认上述配置，再重试；仍不行请联系系统管理员看服务端日志。</p>
      <div class="gate-actions">
        <button type="button" class="gate-btn" @click="auth.signIn()">重试登录</button>
      </div>
    </div>
  </div>
</template>
