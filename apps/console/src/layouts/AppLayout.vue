<script setup lang="ts">
// 控制台外壳：全高导航轨（品牌 + 三个入口 + 当前用户）+ 右侧工作区，与管理端同一套「柜台」
import { computed } from "vue";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();

/** 头像字母：邮箱首字母大写；/api/me 还没回来时用 ? 占位 */
const initial = computed(() => (auth.email || "?").slice(0, 1).toUpperCase());
</script>

<template>
  <nav class="admin-rail" aria-label="控制台">
    <p class="rail-brand">
      <img
        class="rail-wordmark"
        src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
        alt="MintPop"
        width="106"
        height="29"
      />
      <span class="rail-brand-text">
        Lane
        <!-- 「控制台」是身份说明不是产品名，跟在 Lane 后面小一号、灰一档 -->
        <span class="rail-kind">控制台</span>
      </span>
    </p>

    <!-- 入口按用户动线排：先看自己有什么，再去买，最后查单 -->
    <div class="rail-nav">
      <RouterLink
        :to="{ name: 'SUBSCRIPTIONS' }"
        class="rail-link"
        active-class=""
        exact-active-class="router-link-active"
        >我的订阅</RouterLink
      >
      <RouterLink :to="{ name: 'PLANS' }" class="rail-link">购买套餐</RouterLink>
      <RouterLink :to="{ name: 'ORDERS' }" class="rail-link">我的订单</RouterLink>
    </div>

    <div class="rail-foot">
      <div class="rail-user">
        <span class="rail-avatar">{{ initial }}</span>
        <span class="rail-user-email" :title="auth.email">{{ auth.email }}</span>
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
