<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import GateShell from "../components/GateShell.vue";

const auth = useAuthStore();
</script>

<template>
  <GateShell>
    <!-- 登录卡的视觉主体，撑起竖向体量；只有本页放：排障页有大段文字撑着 -->
    <img
      class="gate-mark"
      src="https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png"
      alt=""
      width="128"
      height="128"
    />
    <h1 class="gate-title">统一登录</h1>
    <!-- 控制台不限角色：任何 MintPop 账号都能进来买套餐、看自己的订阅 -->
    <p class="gate-text">请使用你的 <strong>MintPop 账号</strong> 登录</p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="auth.signIn()">MintPop 统一登录</button>
    </div>
  </GateShell>
</template>
