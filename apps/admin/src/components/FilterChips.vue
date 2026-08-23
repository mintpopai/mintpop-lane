<script setup lang="ts" generic="T">
/**
 * 筛选 chip 带：全站表达「我在看哪一批」的唯一形态。
 *
 * 与 tab 的分工——tab 换的是「看哪一类数据」（一次换页，如节点池的第一跳 / 落地），
 * chip 是在那一类里挑一批看。两层不同形，管辖关系才读得出来；两层都是药丸时读不出谁管辖谁。
 *
 * 两个可选字段各有出场条件，别随手加：
 * · count 只在前端握有全量数据时挂——它说的是「这一批有多大」。服务端分页的页面拿不到
 *   分项计数，宁可整条带都不挂，也不做「有的挂有的不挂」。
 * · state 只给有状态语义的批次（上架 / 停用这类），取值与表格里的状态点同一套 [data-state]，
 *   于是 chip 上的色点与它筛出来的那些行是同一个点。
 */
interface Option {
  value: T;
  label: string;
  count?: number;
  state?: string;
}

defineProps<{
  modelValue: T;
  options: readonly Option[];
  /**
   * 这条带在筛什么维度（「按分组筛选」这类）。chip 自己的文字只说是哪一批，
   * 说不出维度，所以这句念给屏幕阅读器听，界面上不显示。
   * 不叫 ariaLabel：`aria-` 开头的名字在模板里会被当成原生属性，落不到 prop 上。
   */
  label: string;
}>();

const emit = defineEmits<{ "update:modelValue": [value: T] }>();
</script>

<template>
  <div class="chip-group" role="group" :aria-label="label">
    <button
      v-for="option in options"
      :key="String(option.value)"
      type="button"
      class="admin-chip"
      :class="{ active: option.value === modelValue }"
      :data-state="option.state"
      :aria-pressed="option.value === modelValue"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
      <span v-if="option.count !== undefined" class="fact">{{ option.count }}</span>
    </button>
  </div>
</template>

<!-- .chip-group 的样式在 layout.css：它要与工具条里其它段落的间距规则一起定，
     放在这里 scoped 的话，那条「段间距比段内宽一档」的规则就跨不过来 -->
