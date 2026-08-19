<script setup lang="ts">
import { useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const route = useRoute();
</script>

<template>
  <el-container style="height: 100%">
    <el-aside width="180px" style="border-right: 1px solid #e4e7ed">
      <div style="padding: 18px 16px; font-weight: 600">Pier 管控后台</div>
      <el-menu :default-active="String(route.name)" router>
        <el-menu-item index="USERS" :route="{ name: 'USERS' }">用户</el-menu-item>
        <el-menu-item index="NODES" :route="{ name: 'NODES' }">节点池</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display: flex; align-items: center; justify-content: flex-end; gap: 12px">
        <span>{{ auth.displayName }}</span>
        <el-button link @click="auth.signOut()">退出登录</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
