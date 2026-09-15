<script setup lang="ts">
// 我的订阅：数据来自 /api/me（守卫每次导航都实探过，store 里就是最新的），本页不再发请求
import { computed } from "vue";
import { useAuthStore } from "../stores/auth";
import PageHead from "../components/PageHead.vue";
import { agentLabel, formatAssignmentNo, formatDateTime, subscriptionState } from "../utils/format";

const auth = useAuthStore();
const subscriptions = computed(() => auth.me?.subscriptions ?? []);
const activeCount = computed(() => subscriptions.value.filter((s) => s.active).length);
</script>

<template>
  <PageHead title="我的订阅">
    <template #facts>
      共 <span class="fact">{{ subscriptions.length }}</span> 份，在期
      <span class="fact">{{ activeCount }}</span> 份
    </template>
  </PageHead>

  <div v-if="subscriptions.length === 0" class="admin-card">
    <div class="sub-empty">
      <p>还没有订阅。挑一个套餐付款后，管理员会为你开通。</p>
      <RouterLink :to="{ name: 'PLANS' }" class="admin-btn">去购买套餐</RouterLink>
    </div>
  </div>

  <ul v-else class="sub-list">
    <li v-for="s in subscriptions" :key="s.id" class="admin-card sub-card">
      <div class="sub-card-head">
        <span class="sub-card-name">{{ s.name }}</span>
        <!-- 三档状态徽标排在 agent 类型之前：`.pill`/`.pill.muted` 选择器都要先命中状态徽标 -->
        <span v-if="subscriptionState(s) === 'PENDING'" class="pill pending">待开通</span>
        <span v-else-if="subscriptionState(s) === 'ACTIVE'" class="pill">在期</span>
        <span v-else class="pill muted">已过期</span>
        <span class="pill muted">{{ agentLabel(s.agentType) }}</span>
      </div>
      <dl class="sub-card-facts">
        <div class="sub-fact">
          <dt>分配号</dt>
          <dd class="fact sub-assignment">{{ formatAssignmentNo(s.assignmentNo) }}</dd>
        </div>
        <template v-if="subscriptionState(s) === 'PENDING'">
          <div class="sub-fact sub-fact-wide">
            <dt>状态</dt>
            <dd>已付款，管理员开通后这里会显示起止时间</dd>
          </div>
        </template>
        <template v-else>
          <div class="sub-fact">
            <dt>起期</dt>
            <dd class="fact">{{ formatDateTime(s.startsAt) }}</dd>
          </div>
          <div class="sub-fact">
            <dt>止期</dt>
            <dd class="fact">{{ formatDateTime(s.endsAt) }}</dd>
          </div>
        </template>
      </dl>
    </li>
  </ul>
</template>
