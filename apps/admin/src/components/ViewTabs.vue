<script setup lang="ts" generic="T">
/**
 * 一级视图 tab：列表页的第一层分批。
 *
 * 与 FilterChips 的分工——**一级用 tab，二级用 chip**。两层筛选同形（都是药丸）时
 * 读不出谁管辖谁，所以一级换形：贴着容器底边的指示条，与导航轨的「你在这里」同一种表达。
 *
 * 一级筛什么由数据说了算：机场订阅按机场，
 * 套餐与企业按 Agent（列相同，保留「全部」总览）。
 *
 * count 是「这一类有多大」，不受二级 chip 影响；二级 chip 上的计数才是在这一类之内的分批规模。
 */
interface Option {
  value: T;
  label: string;
  count?: number;
  /** 名字旁的小标签，标出这一类的属性（如「备用」机场）；不进名字本身，免得括号挤在名字里 */
  tag?: string;
}

defineProps<{
  modelValue: T;
  options: readonly Option[];
  /** 这一层按什么分，念给读屏听。不叫 ariaLabel：`aria-` 开头的名字落不到 prop 上 */
  label: string;
}>();

const emit = defineEmits<{ "update:modelValue": [value: T] }>();
</script>

<template>
  <nav class="admin-tabs" :aria-label="label">
    <button
      v-for="option in options"
      :key="String(option.value)"
      type="button"
      class="admin-tab"
      :class="{ active: option.value === modelValue }"
      :aria-current="option.value === modelValue ? 'true' : undefined"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
      <span v-if="option.tag" class="pill outline tab-tag">{{ option.tag }}</span>
      <span v-if="option.count !== undefined" class="fact">{{ option.count }}</span>
    </button>
  </nav>
</template>

<style scoped>
/* 页签里的标签收小一号，不抢名字 */
.tab-tag {
  padding: 1px 8px;
  font-size: 11px;
}
</style>
