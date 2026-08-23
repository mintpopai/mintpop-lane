<script setup lang="ts">
/**
 * 列表页页头：全站「标题 + 规模事实 + 页面级操作」的唯一容器。
 *
 * 两条规矩钉在这里，所有列表页照做：
 * 1. 页面级操作（新建、导入）只有这一个落点——页头右上。它们与「在看哪一批」无关，
 *    摆进下面的筛选带会和 chip 抢同一条视线；页与页之间落点不一致，也就没有肌肉记忆。
 * 2. facts 讲的是**整页规模**（共 N 个），chip 上的计数讲的是**这一批规模**。
 *    同一个数不在两处各说一遍。
 */
defineProps<{ title: string }>();
</script>

<template>
  <header class="page-head" :class="{ 'with-actions': $slots.actions }">
    <div class="page-head-text">
      <h2 class="page-title">{{ title }}</h2>
      <p class="page-facts"><slot name="facts" /></p>
    </div>
    <div v-if="$slots.actions" class="page-head-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
