<script setup lang="ts">
import { useAuthStore } from "../stores/auth";
import GateShell from "../components/GateShell.vue";

const auth = useAuthStore();
</script>

<template>
  <GateShell wide>
    <h1 class="gate-title">这个账号没有后台权限</h1>
    <!-- 直接打开本页（公开路由，守卫不探 /api/me）时拿不到邮箱，就不点名 -->
    <p v-if="auth.email" class="gate-text gate-text-long">
      当前登录的是 <span class="fact">{{ auth.email }}</span
      >。找系统管理员把它设为管理员，或换个账号登录。
    </p>
    <p v-else class="gate-text gate-text-long">找系统管理员把这个账号设为管理员，或换个账号登录。</p>
    <div class="gate-actions">
      <button type="button" class="gate-btn" @click="auth.signOut()">退出登录</button>
      <a class="gate-link" href="https://lane.mintpop.ai">返回官网</a>
    </div>
  </GateShell>
</template>
