<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { PLACEHOLDER } from "../utils/format";

/**
 * 一格放不下的文本：单行截断成省略号，悬浮（或聚焦）看全文。
 *
 * 替掉原生 title：后者要等一两秒才弹、样式无从控制、键盘用户摸不到，
 * 而且不管文本有没有真被截断都照弹。气泡 Teleport 到 body + fixed 定位，
 * 与 AdminSelect 的面板同一套做法——表格在 .admin-card 里横向滚动，
 * absolute 定位会被 overflow 裁掉。
 *
 * 两处排版上的讲究，改模板时别破坏：
 * 1. 组件必须保持单根（模板顶层只有那个 span）。多根 fragment 收不到外部传进来的
 *    class/attr，测试里也拿不到根元素。注意模板顶层的注释节点同样算一个根节点，
 *    所以这段说明写在这里而不是模板里；Teleport 也因此写在 span 内部——它的内容
 *    会被搬到 body，原地只留一个注释占位，不参与排版。
 * 2. span 的标签与文本之间不能留空白：nowrap 下换行会被渲染成前后各一个空格，
 *    把这一格的左缘顶歪。
 */
const props = withDefaults(
  defineProps<{
    text: string | null;
    /** 没有内容时占位，默认取全站统一的占位符 */
    placeholder?: string;
    /** 截断宽度，超过即出省略号 */
    maxWidth?: number;
  }>(),
  { placeholder: PLACEHOLDER, maxWidth: 220 },
);

/** 悬浮到弹出的延迟。原生 title 要等一两秒太钝，零延迟又会让鼠标扫过一列时一路闪 */
const SHOW_DELAY_MS = 120;
/** 与 .tip 的 max-width 同值，用于把气泡夹在视口内 */
const TIP_MAX_WIDTH = 360;
/** 气泡与锚点的贴距，以及与视口边缘的余量 */
const GAP = 4;
const EDGE = 8;

const anchor = ref<HTMLElement | null>(null);
const visible = ref(false);
const tipStyle = ref<Record<string, string>>({});
let timer: ReturnType<typeof setTimeout> | null = null;

/** 文本没被截断就没有「看全文」的需求，再弹一个一模一样的气泡纯属噪声 */
function isTruncated(): boolean {
  const el = anchor.value;
  return el !== null && el.scrollWidth > el.clientWidth;
}

/** 贴着锚点下方左对齐画；下方放不下就翻到上方，右缘超出视口就向左夹住 */
function place(): void {
  const rect = anchor.value?.getBoundingClientRect();
  if (!rect) {
    return;
  }
  const below = window.innerHeight - rect.bottom - GAP - EDGE;
  const above = rect.top - GAP - EDGE;
  const up = below < 80 && above > below;
  tipStyle.value = {
    left: `${Math.max(EDGE, Math.min(rect.left, window.innerWidth - TIP_MAX_WIDTH - EDGE))}px`,
    ...(up
      ? { bottom: `${window.innerHeight - rect.top + GAP}px` }
      : { top: `${rect.bottom + GAP}px` }),
  };
}

function show(): void {
  if (!props.text || !isTruncated()) {
    return;
  }
  timer = setTimeout(() => {
    place();
    visible.value = true;
    // 捕获阶段监听 scroll：锚点在 .admin-card 这个内部滚动容器里，冒泡阶段收不到
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
  }, SHOW_DELAY_MS);
}

function hide(): void {
  if (timer !== null) {
    clearTimeout(timer);
    timer = null;
  }
  visible.value = false;
  window.removeEventListener("scroll", place, true);
  window.removeEventListener("resize", place);
}

onBeforeUnmount(hide);
</script>

<template>
  <span
    ref="anchor"
    class="truncated"
    :style="{ maxWidth: `${maxWidth}px` }"
    :tabindex="text ? 0 : undefined"
    @mouseenter="show()"
    @mouseleave="hide()"
    @focus="show()"
    @blur="hide()"
    >{{ text || placeholder
    }}<Teleport to="body">
      <div v-if="visible" class="tip" role="tooltip" :style="tipStyle">{{ text }}</div>
    </Teleport></span
  >
</template>

<style scoped>
/* inline-block 才让 max-width 生效；vertical-align 兜住基线偏移，
   否则这一格会比同行其它格矮一点、把行高撑开 */
.truncated {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  vertical-align: bottom;
}

/* 可聚焦但不是控件，所以只在键盘落点上描一圈，鼠标点击不留痕 */
.truncated:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
  border-radius: 2px;
}

/* 白面 + 描边 + 浅投影：与 AdminSelect 面板、弹窗同一种「盖在页面之上」的材质。
   不画箭头，靠 4px 贴距表明归属，与仓里其它浮层一致 */
.tip {
  position: fixed;
  z-index: 60;
  max-width: 360px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  box-shadow: 0 12px 32px rgba(15, 26, 22, 0.14);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-ink);
  /* 气泡是拿来读全文的，这里必须能换行，且长串无断点时也要断开 */
  white-space: normal;
  overflow-wrap: anywhere;
  pointer-events: none;
}
</style>
