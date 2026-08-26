<script setup lang="ts">
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
</script>

<template>
  <div class="gate">
    <div class="gate-box">
      <p class="gate-brand"><span class="wordmark">MintPop</span> Lane 管理后台</p>
      <h1 class="gate-title">这个账号没有后台权限</h1>
      <p class="gate-text">
        你已经登录成功，但这个账号不是管理员，因此看不到任何数据。
        需要开通请联系系统管理员——授予管理员要直接改数据库，页面上没有这个入口。
      </p>
      <div class="gate-actions">
        <button type="button" class="gate-btn" @click="auth.signOut()">退出登录</button>
      </div>
    </div>
  </div>
</template>
