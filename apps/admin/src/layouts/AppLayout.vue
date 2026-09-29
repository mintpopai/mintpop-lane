<script setup lang="ts">
// 后台外壳：全高导航轨（品牌 + 页面 + 当前用户）+ 右侧工作区
import { computed, onMounted } from "vue";
import { useAuthStore } from "../stores/auth";
import { useRebindStore } from "../stores/rebind";

const auth = useAuthStore();
const rebind = useRebindStore();

/** 头像字母：邮箱首字母大写；/api/me 还没回来时用 ? 占位 */
const initial = computed(() => (auth.email || "?").slice(0, 1).toUpperCase());

// 外壳只在进后台时取一次待办数。之后由「同意 / 拒绝」就地更新（见 DeviceRequestsView）
onMounted(() => rebind.refresh());
</script>

<template>
  <nav class="admin-rail" aria-label="管理后台">
    <!-- 品牌锁定组合与闸门页逐项同读法：字标 → 竖线 → Lane → 管理后台，只是轨宽
         只有 208px，整组等比降一档才塞得下。标了尺寸，图没到之前不抖版 -->
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
        <!-- 「管理后台」是身份说明不是产品名，跟在 Lane 后面小一号、灰一档 -->
        <span class="rail-kind">管理后台</span>
      </span>
    </p>

    <div class="rail-nav">
      <RouterLink :to="{ name: 'USERS' }" class="rail-link">用户</RouterLink>
      <!-- 待办数只在大于 0 时出现：常驻一个 0 会训练人忽略它 -->
      <RouterLink :to="{ name: 'DEVICE_REQUESTS' }" class="rail-link">
        换机申请
        <span v-if="rebind.pendingCount > 0" class="rail-badge">{{ rebind.pendingCount }}</span>
      </RouterLink>
      <RouterLink :to="{ name: 'AIRPORTS' }" class="rail-link">机场</RouterLink>
      <RouterLink :to="{ name: 'NODES' }" class="rail-link">节点池</RouterLink>
      <RouterLink :to="{ name: 'PLANS' }" class="rail-link">套餐</RouterLink>
      <RouterLink :to="{ name: 'ENTERPRISES' }" class="rail-link">企业</RouterLink>
      <RouterLink :to="{ name: 'LINK_HEALTH' }" class="rail-link">链路健康</RouterLink>
      <RouterLink :to="{ name: 'SETTINGS' }" class="rail-link">全局配置</RouterLink>
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
