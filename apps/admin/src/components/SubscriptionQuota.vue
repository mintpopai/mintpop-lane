<script setup lang="ts">
// 订阅流量额度：机场订阅列表与订阅详情共用同一种画法
import { computed } from "vue";
import type { AirportSubscriptionResponse } from "../api/types";
import { quotaPercent } from "../utils/subscriptionQuota";

const props = defineProps<{
  subscription: Pick<AirportSubscriptionResponse, "usedBytes" | "totalBytes">;
}>();

const percent = computed(() => quotaPercent(props.subscription));
</script>

<template>
  <span v-if="percent !== null" class="quota">
    <span class="quota-bar">
      <span class="quota-bar-fill" :style="{ width: `${percent}%` }" />
    </span>
    <span class="fact quota-pct">{{ percent }}%</span>
  </span>
  <!-- 额度头是否存在，与「用了多少」是两回事：拿不到时绝不能显示成 0% -->
  <span v-else class="muted quota-none">未提供</span>
</template>

<style scoped>
.quota {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.quota-bar {
  display: inline-block;
  width: 96px;
  height: 6px;
  background: var(--color-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.quota-bar-fill {
  display: block;
  height: 100%;
  background: var(--color-brand-deep);
  border-radius: var(--radius-pill);
}
</style>
