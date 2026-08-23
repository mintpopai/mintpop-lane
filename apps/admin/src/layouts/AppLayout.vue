<script setup lang="ts">
// 后台外壳：全高导航轨（品牌 + 页面 + 当前用户）+ 右侧工作区
import { computed } from "vue";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();

/** 头像兜底字母：显示名首字（displayName 兜过 email，不会为空串才取） */
const initial = computed(() => (auth.displayName || "?").slice(0, 1));
</script>

<template>
  <nav class="admin-rail" aria-label="管控后台">
    <p class="rail-brand">
      <span class="wordmark rail-wordmark">MintPop</span>
      <span class="rail-kind">Lane 管控后台</span>
    </p>

    <div class="rail-nav">
      <RouterLink :to="{ name: 'USERS' }" class="rail-link">用户</RouterLink>
      <RouterLink :to="{ name: 'NODES' }" class="rail-link">节点池</RouterLink>
      <RouterLink :to="{ name: 'PLANS' }" class="rail-link">套餐</RouterLink>
    </div>

    <div class="rail-foot">
      <div class="rail-user">
        <span class="rail-avatar">{{ initial }}</span>
        <span class="rail-user-name">{{ auth.displayName }}</span>
      </div>
      <button type="button" class="rail-signout" @click="auth.signOut()">退出登录</button>
    </div>
  </nav>

  <main class="admin-desk">
    <div class="admin-page">
      <router-view />
    </div>
  </main>
</template>
