<script setup lang="ts">
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
</script>

<template>
  <main style="padding: 48px; text-align: center">
    <h2>无管理权限</h2>
    <p>你已经登录成功，但这个账号在管控后台里不是管理员，因此看不到任何数据。</p>
    <p>需要开通请联系系统管理员——授予管理员要直接改数据库，页面上没有这个入口。</p>
    <el-button style="margin-top: 16px" @click="auth.signOut()">退出登录</el-button>
  </main>
</template>
