<script setup lang="ts">
// 下拉选择：替代原生 <select>。
// 原生 select 的弹层由操作系统绘制，CSS 一律改不动——系统蓝高亮、系统勾号、系统圆角，
// 和这套「柜台」材质对不上，所以自己实现一个 listbox。
//
// 无障碍按 ARIA APG 的 select-only combobox 模式：焦点始终留在触发按钮上，
// 用 aria-activedescendant 指出「当前落在哪一项」，不把焦点移进列表。
// 面板 Teleport 到 body + fixed 定位：弹窗内容区是 overflow-y: auto，
// 面板若留在原地会被裁掉。
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

// 相对 shop 版本的唯一扩展：允许 null 取值，承载「不分配」这类可清空的选择
type OptionValue = string | number | boolean | null

interface SelectOption {
  value: OptionValue
  /** 可选色点，给「值本身有颜色」的选项用（如主题色） */
  dot?: string
  label: string
}

const props = defineProps<{
  modelValue: OptionValue
  options: SelectOption[]
  /** 没有可见 label 时给屏幕阅读器用。给了 prefix 就不必再给它——触发器上的文字本身就是名字 */
  ariaLabel?: string
  id?: string
  /** 选项是枚举值这类「系统生成的事实」时置真，走等宽——与表格里同一个值的排版对齐 */
  mono?: boolean
  /**
   * 维度名（「状态」「每页」），弱色显示在当前值左边，只出现在触发器上、不进选项面板。
   *
   * 收起来的下拉只显示当前取值（「上架」），不写维度就读不出这是在筛什么。维度名做成
   * 控件外面一段裸文字最难看：一条带上其它东西都是有边框的实体，只有它是浮在页面底色上的
   * 灰字，节奏被打断。收进控件本体，它就和搜索框、按钮是同一种材质了。
   * 面板里不重复它——展开时上下文已经足够，每行都带前缀纯属啰嗦。
   */
  prefix?: string
  /** 当前值不是默认值（正在筛）。已筛的控件亮起来，未筛的保持安静，不跟表格抢视线 */
  filtered?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [OptionValue] }>()

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const open = ref(false)
/** 键盘/鼠标当前落在哪一项——与「已选中哪一项」是两回事 */
const activeIndex = ref(0)
const panelStyle = ref<Record<string, string>>({})

/** 面板 id 要稳定且唯一：aria-controls / aria-activedescendant 都指向它 */
const uid = `sel-${nextInstanceId()}`
const selected = computed(() => props.options.find((o) => o.value === props.modelValue))
const optionId = (i: number) => `${uid}-opt-${i}`

/** 面板贴着触发器画；下方放不下就翻到上方 */
function place() {
  const rect = trigger.value?.getBoundingClientRect()
  if (!rect) {
    return
  }
  const gap = 4
  const below = window.innerHeight - rect.bottom - gap - 8
  const above = rect.top - gap - 8
  const up = below < 160 && above > below
  panelStyle.value = {
    left: `${rect.left}px`,
    minWidth: `${rect.width}px`,
    maxHeight: `${Math.min(280, up ? above : below)}px`,
    ...(up ? { bottom: `${window.innerHeight - rect.top + gap}px` } : { top: `${rect.bottom + gap}px` }),
  }
}

function onOutside(event: MouseEvent) {
  const target = event.target as Node
  if (!root.value?.contains(target) && !panel.value?.contains(target)) {
    open.value = false
  }
}

watch(open, async (isOpen) => {
  if (!isOpen) {
    window.removeEventListener('scroll', place, true)
    window.removeEventListener('resize', place)
    document.removeEventListener('mousedown', onOutside)
    return
  }
  activeIndex.value = Math.max(
    0,
    props.options.findIndex((o) => o.value === props.modelValue),
  )
  place()
  // 捕获阶段监听 scroll：面板可能开在弹窗这类内部滚动容器之上，冒泡阶段收不到
  window.addEventListener('scroll', place, true)
  window.addEventListener('resize', place)
  document.addEventListener('mousedown', onOutside)
  await nextTick()
  scrollActiveIntoView()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', place, true)
  window.removeEventListener('resize', place)
  document.removeEventListener('mousedown', onOutside)
})

function scrollActiveIntoView() {
  panel.value?.children[activeIndex.value]?.scrollIntoView({ block: 'nearest' })
}

function move(step: number) {
  const count = props.options.length
  if (count === 0) {
    return
  }
  activeIndex.value = (activeIndex.value + step + count) % count
  scrollActiveIntoView()
}

function choose(index: number) {
  const option = props.options[index]
  if (option) {
    emit('update:modelValue', option.value)
  }
  open.value = false
  trigger.value?.focus()
}

function onKeydown(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowDown':
    case 'ArrowUp':
      event.preventDefault()
      if (!open.value) {
        open.value = true
      } else {
        move(event.key === 'ArrowDown' ? 1 : -1)
      }
      break
    case 'Enter':
    case ' ':
      event.preventDefault()
      if (open.value) {
        choose(activeIndex.value)
      } else {
        open.value = true
      }
      break
    case 'Escape':
      if (open.value) {
        // 弹窗里也有 Esc 关闭：下拉开着时先关下拉，别把整个弹窗一起关了
        event.stopPropagation()
        open.value = false
      }
      break
    case 'Home':
    case 'End':
      if (open.value) {
        event.preventDefault()
        activeIndex.value = event.key === 'Home' ? 0 : props.options.length - 1
        scrollActiveIntoView()
      }
      break
    case 'Tab':
      open.value = false
      break
  }
}
</script>

<script lang="ts">
/** 每个实例一个稳定 id，供 aria-controls / aria-activedescendant 引用 */
let instanceCount = 0

function nextInstanceId(): number {
  instanceCount += 1
  return instanceCount
}
</script>

<template>
  <div ref="root" class="sel">
    <button
      :id="id"
      ref="trigger"
      type="button"
      class="admin-select sel-trigger"
      role="combobox"
      :aria-expanded="open"
      :aria-controls="uid"
      :aria-label="ariaLabel"
      :aria-activedescendant="open ? optionId(activeIndex) : undefined"
      :class="{ open, filtered }"
      @click="open = !open"
      @keydown="onKeydown"
    >
      <span v-if="prefix" class="sel-prefix">{{ prefix }}</span>
      <span v-if="selected?.dot" class="accent-dot" :style="{ background: selected.dot }"></span>
      <span class="sel-value" :class="{ fact: mono }">{{ selected?.label ?? '' }}</span>
    </button>

    <Teleport to="body">
      <ul
        v-if="open"
        :id="uid"
        ref="panel"
        class="sel-panel"
        role="listbox"
        :aria-label="ariaLabel"
        :style="panelStyle"
      >
        <li
          v-for="(option, index) in options"
          :id="optionId(index)"
          :key="String(option.value)"
          class="sel-option"
          role="option"
          :class="{ active: index === activeIndex }"
          :aria-selected="option.value === modelValue"
          @click="choose(index)"
          @mousemove="activeIndex = index"
        >
          <!-- 选中项同时给勾号和底色，不靠颜色单独传达 -->
          <span class="sel-check" aria-hidden="true">{{ option.value === modelValue ? '✓' : '' }}</span>
          <span v-if="option.dot" class="accent-dot" :style="{ background: option.dot }"></span>
          <span class="sel-option-label" :class="{ fact: mono }">{{ option.label }}</span>
        </li>
      </ul>
    </Teleport>
  </div>
</template>

<style scoped>
/* inline-flex：在工具条里按内容宽度排，在表单字段（flex 纵列）里被拉满，
   与同行的 input / textarea 等宽，不用百分比宽度去兜 */
.sel {
  position: relative;
  display: inline-flex;
}

/* 触发器复用 .admin-select 的盒模型（同高 36px、同圆角、同 chevron），
   只把 button 的默认排版扳回左对齐 */
.sel-trigger {
  display: inline-flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  text-align: left;
  cursor: pointer;
}

/* 面板开着时 chevron 翻上来，和状态对上 */
.sel-trigger.open {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 6'%3E%3Cpath d='M1 5l4-4 4 4' fill='none' stroke='%236b7280' stroke-width='1.4' stroke-linecap='round' stroke-linejoin='round'/%3E%3C/svg%3E");
}

/* 维度名比当前值弱一档：一眼扫到的是「现在筛的是什么值」，维度是背景信息 */
.sel-prefix {
  flex-shrink: 0;
  margin-right: 6px;
  color: var(--color-ink-secondary);
}

.sel-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 已生效的筛选用薄荷底标出来，与面板里「已选项」同一种语言；未筛时保持白底，
   一条带上默认全是安静的，不跟表格抢视线。底色之外还有值本身可读，不靠颜色单独传达 */
.sel-trigger.filtered {
  border-color: color-mix(in srgb, var(--color-brand) 55%, #ffffff);
  background-color: color-mix(in srgb, var(--color-brand) 14%, #ffffff);
}

/* 面板：白面 + 描边 + 一层浅投影，和弹窗、纸带浮层同一种「盖在页面之上」的材质 */
.sel-panel {
  position: fixed;
  z-index: 50;
  overflow-y: auto;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg);
  box-shadow: 0 12px 32px rgba(15, 26, 22, 0.14);
  list-style: none;
}

.sel-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-button);
  font-size: 14px;
  line-height: 18px;
  color: var(--color-ink);
  white-space: nowrap;
  cursor: pointer;
}

/* 键盘与鼠标共用同一个「当前落点」高亮，不做两套 */
.sel-option.active {
  background: var(--color-bg-cloud);
}

.sel-option[aria-selected='true'] {
  font-weight: 600;
}

.sel-option[aria-selected='true'].active {
  background: color-mix(in srgb, var(--color-brand) 14%, #ffffff);
}

/* 勾号占位始终存在，选中与否不改变文字的左边界 */
.sel-check {
  flex-shrink: 0;
  width: 12px;
  color: var(--color-brand-deep);
  font-size: 12px;
}

.sel-option-label {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
