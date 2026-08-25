<script setup lang="ts">
/**
 * 线路三节点图：本机 → 专属线路 → 出口。
 *
 * 这不是装饰：桌面端主页顶上那张图就长这样（见 desktop `src/components/LanePath.vue`），
 * 官网版只是放大、加了标注，形状与配色一致。哪一段没通就在哪一段上标出来，
 * 颜色永远配一个字形（✓ / ✕ / !），不靠颜色单独传达状态。
 */
import { computed } from "vue";

/** 节点标记与连线形态，与桌面端 link.ts 的 Mark / Seg 同名同义 */
type Mark = "ok" | "fail" | "warn" | "pending" | "off";
type Seg = "on" | "flow" | "broken" | "warn" | "off";

const props = withDefaults(
  defineProps<{
    /** 展示哪一种处境的形状 */
    variant?: "active" | "connecting" | "unreachable" | "mismatch" | "off";
    /** 是否显示节点下方的说明文字 */
    labels?: boolean;
    size?: "md" | "lg";
  }>(),
  { variant: "active", labels: true, size: "md" },
);

/** 各 variant 的形状。取值与桌面端 present 表里对应处境的 nodes/segs 逐一对齐 */
const shapes: Record<
  NonNullable<typeof props.variant>,
  { nodes: [Mark, Mark, Mark]; segs: [Seg, Seg]; aria: string }
> = {
  active: { nodes: ["ok", "ok", "ok"], segs: ["on", "on"], aria: "线路状态：已接通" },
  connecting: {
    nodes: ["ok", "ok", "pending"],
    segs: ["on", "flow"],
    aria: "线路状态：正在接通",
  },
  unreachable: {
    nodes: ["ok", "fail", "off"],
    segs: ["broken", "off"],
    aria: "线路状态：连不上",
  },
  mismatch: {
    nodes: ["ok", "ok", "warn"],
    segs: ["on", "warn"],
    aria: "线路状态：出口对不上",
  },
  off: { nodes: ["ok", "off", "off"], segs: ["off", "off"], aria: "线路状态：还没分配线路" },
};

const shape = computed(() => shapes[props.variant]);

const glyph: Record<Mark, string> = { ok: "✓", fail: "✕", warn: "!", pending: "", off: "" };
const names = ["本机", "专属线路", "出口"] as const;
const hints = ["你的电脑", "专门给你的", "只有你在走"] as const;
</script>

<template>
  <div :class="['lane', size, { bare: !labels }]" role="img" :aria-label="shape.aria">
    <template v-for="(name, i) in names" :key="name">
      <div v-if="i > 0" :class="['seg', shape.segs[i - 1]]"></div>
      <div class="node">
        <span :class="['mark', shape.nodes[i]]" aria-hidden="true">{{ glyph[shape.nodes[i]] }}</span>
        <template v-if="labels">
          <span class="name">{{ name }}</span>
          <span class="hint">{{ hints[i] }}</span>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.lane {
  display: flex;
  align-items: flex-start;
  --dot: 32px;
}

.lane.lg {
  --dot: 44px;
}

.node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 88px;
  flex: none;
}

.lane.lg .node {
  width: 116px;
}

/* 不带文字标注时节点按圆点收缩，否则固定宽度会在圆点两侧留出大片空白 */
.lane.bare .node {
  width: auto;
}

.lane.bare .seg {
  margin-left: 10px;
  margin-right: 10px;
}

.mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--dot);
  height: var(--dot);
  font-size: calc(var(--dot) * 0.42);
  font-weight: 700;
  line-height: 1;
  border-radius: 50%;
  border: 2px solid var(--line);
  color: var(--ink-3);
  background: var(--bg);
}

.mark.ok {
  border-color: var(--mint-deep);
  color: #ffffff;
  background: var(--mint-deep);
}

.mark.fail {
  border-color: var(--danger);
  color: var(--danger);
  background: var(--danger-soft);
}

.mark.warn {
  border-color: var(--warn);
  color: var(--warn);
  background: var(--warn-soft);
}

.mark.pending {
  border-style: dashed;
}

.name {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
}

.hint {
  font-size: 12px;
  color: var(--ink-3);
  margin-top: -4px;
}

/* 连线与节点圆心对齐：圆半径 - 线半高 */
.seg {
  flex: 1;
  height: 2px;
  margin: calc(var(--dot) / 2 - 1px) -18px 0;
  background: var(--line);
  border-radius: 1px;
  min-width: 24px;
}

.seg.on {
  background: var(--mint-deep);
}

.seg.warn {
  background: repeating-linear-gradient(90deg, var(--warn) 0 6px, transparent 6px 12px);
}

.seg.broken {
  background: repeating-linear-gradient(90deg, var(--danger) 0 6px, transparent 6px 12px);
}

/* 连接中：虚线打底，一段薄荷色光带向出口方向滑动。
   只动 transform（交给合成器，不逐帧重绘） */
.seg.flow {
  position: relative;
  overflow: hidden;
  background: repeating-linear-gradient(90deg, var(--ink-3) 0 6px, transparent 6px 12px);
}

.seg.flow::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 40%;
  background: var(--mint-deep);
}

@media (prefers-reduced-motion: no-preference) {
  .seg.flow::after {
    animation: flow 1.6s ease-in-out infinite;
  }
  @keyframes flow {
    from {
      transform: translateX(-100%);
    }
    to {
      transform: translateX(350%);
    }
  }
}
</style>
