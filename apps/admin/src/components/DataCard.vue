<script setup lang="ts">
/**
 * 列表页数据区：加载 / 错误 / 空 / 有数据四态都在这张卡里换，卡框恒在。
 *
 * 三态原先散在卡外（裸着的一行灰字），加载完成那一刻卡框凭空出现、整页往下跳一次——
 * 四个页面共同的老毛病，收进来一并治掉。四态共用同一个居中盒子且带最小高度，
 * 于是「加载中 → 出错」「加载中 → 空」这类状态之间的切换完全不跳。
 */
defineProps<{
  loading?: boolean;
  /** 非空即错误态。文案直接用服务端给的中文提示，不另编一套话术 */
  error?: string;
  /** 加载完成且一条都没有 */
  empty?: boolean;
  /** 空态说明：说清为什么空、下一步做什么 */
  emptyText?: string;
}>();
</script>

<template>
  <div class="admin-card">
    <p v-if="loading" class="card-state" role="status">加载中……</p>
    <p v-else-if="error" class="card-state error" role="alert">{{ error }}</p>
    <div v-else-if="empty" class="card-state">
      <p class="card-state-text">{{ emptyText }}</p>
      <!-- 空屏是一次邀请：这里能做的那件事就摆在说明底下，不让人自己回头去页头找 -->
      <div class="card-state-actions"><slot name="empty-action" /></div>
    </div>
    <slot v-else />
  </div>
</template>

<style scoped>
/* 最小高度让「什么都还没有」也是一块有厚度的面，而不是一条塌下去的窄边；
   居中是因为这里没有数据可对齐——左对齐一行字会被读成表格的第一行 */
.card-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  min-height: 200px;
  padding: 40px 24px;
  text-align: center;
  color: var(--color-ink-secondary);
  font-size: 14px;
  line-height: 1.75;
}

.card-state.error {
  color: var(--counter-danger);
}

/* 一行说明控制在读得完的长度内，不横穿整张宽卡 */
.card-state-text {
  max-width: 46ch;
}

/* 空态可以摆不止一个动作（如节点池的「从订阅导入」+「新建节点」），横排，
   与页头右上那对按钮同序：次要在左、主操作在右 */
.card-state-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

/* 没给动作时别撑出一段空行 */
.card-state-actions:empty {
  display: none;
}
</style>
