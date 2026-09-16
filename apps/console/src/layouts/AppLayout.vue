<script setup lang="ts">
// 控制台外壳：顶栏（品牌 + 三个 tab + 当前用户）常驻页首，下方是工作区。
// 与管理端的全高左轨不同——控制台只有三个入口，横排一行足够，把整幅宽度让给内容。
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();

/** 头像字母：邮箱首字母大写；/api/me 还没回来时用 ? 占位 */
const initial = computed(() => (auth.email || "?").slice(0, 1).toUpperCase());

const menuOpen = ref(false);
/** 胶囊与菜单共处一个容器，用它判定「点的是不是自己人」 */
const accountRef = ref<HTMLElement | null>(null);

function closeMenu(): void {
  menuOpen.value = false;
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape") {
    closeMenu();
  }
}

// 点别处收起。不用「全屏遮罩」那套：顶栏是 sticky 且后续可能加 backdrop-filter，
// 遮罩的定位包含块会被它改掉；文档级监听 + contains 判定与布局无关，稳。
function onPointerDown(event: Event): void {
  const target = event.target as Node | null;
  if (menuOpen.value && target && !accountRef.value?.contains(target)) {
    closeMenu();
  }
}

onMounted(() => {
  window.addEventListener("keydown", onKeydown);
  document.addEventListener("pointerdown", onPointerDown);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
  document.removeEventListener("pointerdown", onPointerDown);
});
</script>

<template>
  <header class="admin-bar">
    <p class="bar-brand">
      <img
        class="bar-wordmark"
        src="https://standards.mintpop.ai/assets/brand/wordmark/mintpop-wordmark-dark.png"
        alt="MintPop"
        width="106"
        height="29"
      />
      <span class="bar-brand-text">
        Lane
        <!-- 「控制台」是身份说明不是产品名，跟在 Lane 后面小一号、灰一档 -->
        <span class="bar-kind">控制台</span>
      </span>
    </p>

    <!-- 入口按用户动线排：先看自己有什么，再去买，最后查单 -->
    <nav class="bar-nav" aria-label="控制台">
      <RouterLink
        :to="{ name: 'SUBSCRIPTIONS' }"
        class="bar-link"
        active-class=""
        exact-active-class="router-link-active"
        >我的订阅</RouterLink
      >
      <RouterLink :to="{ name: 'PLANS' }" class="bar-link">购买套餐</RouterLink>
      <RouterLink :to="{ name: 'ORDERS' }" class="bar-link">我的订单</RouterLink>
    </nav>

    <div ref="accountRef" class="bar-account">
      <button
        type="button"
        class="bar-user"
        aria-haspopup="menu"
        :aria-expanded="menuOpen"
        @click="menuOpen = !menuOpen"
      >
        <span class="bar-user-email">{{ auth.email }}</span>
        <span class="bar-avatar">{{ initial }}</span>
      </button>

      <div v-if="menuOpen" class="bar-menu" role="menu">
        <!-- 胶囊里的邮箱会被截断，菜单里给全文（窄屏胶囊只剩头像，这里是唯一读得到身份的地方） -->
        <p class="bar-menu-email">{{ auth.email }}</p>
        <button type="button" class="bar-signout" role="menuitem" @click="auth.signOut()">
          退出登录
        </button>
      </div>
    </div>
  </header>

  <main class="admin-desk">
    <div class="admin-page">
      <router-view />
    </div>
  </main>
</template>
