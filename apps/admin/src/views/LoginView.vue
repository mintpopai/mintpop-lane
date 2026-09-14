<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import GateShell from "../components/GateShell.vue";

const auth = useAuthStore();
</script>

<template>
  <GateShell>
    <!-- 登录卡的视觉主体，撑起竖向体量——飞书那张卡是被二维码撑起来的，我们没有二维码，
         就用产品图标顶上。只有本页放：排障页与无权限页有大段文字撑着，再加图标会撑过头 -->
    <img
      class="gate-mark"
      src="https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png"
      alt=""
      width="128"
      height="128"
    />
    <h1 class="gate-title">统一登录</h1>
    <!-- 点名用哪种账号（准入条件），不是罗列后台能干什么——那些进门就看见了，不在门口讲 -->
    <p class="gate-text">请使用 <strong>管理员账号</strong> 登录</p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="auth.signIn()">MintPop 统一登录</button>
    </div>
  </GateShell>
</template>
