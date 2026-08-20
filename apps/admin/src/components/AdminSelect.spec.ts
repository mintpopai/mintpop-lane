import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import Select from './AdminSelect.vue'

const OPTIONS = [
  { value: 'MINT', label: 'MINT', dot: '#17d1a7' },
  { value: 'VIOLET', label: 'VIOLET', dot: '#6d5bd0' },
  { value: 'SKY', label: 'SKY', dot: '#2f7fd1' },
]

let wrapper: VueWrapper | null = null

function render(props: Partial<InstanceType<typeof Select>['$props']> = {}) {
  wrapper = mount(Select, {
    attachTo: document.body,
    props: { modelValue: 'MINT', options: OPTIONS, ariaLabel: '主题色', ...props },
  })
  return wrapper
}

function trigger(): HTMLElement {
  return document.querySelector('.sel-trigger') as HTMLElement
}

/** 面板 Teleport 到 body，不在 wrapper 根下 */
function panel(): HTMLElement | null {
  return document.querySelector('.sel-panel')
}

function options(): HTMLElement[] {
  return Array.from(document.querySelectorAll<HTMLElement>('.sel-option'))
}

async function press(key: string) {
  trigger().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }))
  await nextTick()
  await nextTick()
}

async function open() {
  await press('ArrowDown')
}

beforeEach(() => {
  // jsdom 不实现 scrollIntoView，组件在移动高亮项时会调它
  Element.prototype.scrollIntoView = vi.fn()
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('触发器语义', () => {
  it('按 ARIA 的 select-only combobox 模式暴露角色与状态', () => {
    render()

    expect(trigger().getAttribute('role')).toBe('combobox')
    expect(trigger().getAttribute('aria-expanded')).toBe('false')
    expect(trigger().getAttribute('aria-label')).toBe('主题色')
  })

  it('展示当前选中项的文案与色点', () => {
    render({ modelValue: 'VIOLET' })

    expect(document.querySelector('.sel-value')?.textContent).toBe('VIOLET')
    expect(document.querySelector('.sel-trigger .accent-dot')).not.toBeNull()
  })

  it('选中值不在选项里时显示空，不渲染 undefined', () => {
    render({ modelValue: 'UNKNOWN' })

    expect(document.querySelector('.sel-value')?.textContent).toBe('')
  })

  it('mono 为真时给值加等宽标记，与表格里同一个值的排版对齐', () => {
    render({ mono: true })

    expect(document.querySelector('.sel-value')?.classList.contains('fact')).toBe(true)
  })
})

describe('打开与关闭', () => {
  it('点击触发器展开面板', async () => {
    render()

    await trigger().click()
    await nextTick()

    expect(panel()).not.toBeNull()
    expect(trigger().getAttribute('aria-expanded')).toBe('true')
  })

  it('再次点击收起面板', async () => {
    render()

    await trigger().click()
    await nextTick()
    await trigger().click()
    await nextTick()

    expect(panel()).toBeNull()
  })

  it('方向键在收起状态下只负责展开，不顺带移动高亮', async () => {
    render()

    await open()

    expect(panel()).not.toBeNull()
    expect(options()[0].classList.contains('active')).toBe(true)
  })

  it('展开时把高亮定位到当前选中项，而不是永远从第一项开始', async () => {
    render({ modelValue: 'SKY' })

    await open()

    expect(options()[2].classList.contains('active')).toBe(true)
    expect(trigger().getAttribute('aria-activedescendant')).toBe(options()[2].id)
  })

  it('点击组件外部收起面板', async () => {
    render()
    await open()

    document.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await nextTick()

    expect(panel()).toBeNull()
  })

  it('Tab 收起面板，把焦点交还给页面的正常顺序', async () => {
    render()
    await open()

    await press('Tab')

    expect(panel()).toBeNull()
  })

  it('Esc 只收起下拉、不让事件继续冒泡（外层弹窗的 Esc 不该被一起触发）', async () => {
    render()
    await open()

    const outer = vi.fn()
    document.addEventListener('keydown', outer)
    await press('Escape')
    document.removeEventListener('keydown', outer)

    expect(panel()).toBeNull()
    expect(outer).not.toHaveBeenCalled()
  })

  it('面板已收起时 Esc 不拦截，交给外层弹窗处理', async () => {
    render()

    const outer = vi.fn()
    document.addEventListener('keydown', outer)
    await press('Escape')
    document.removeEventListener('keydown', outer)

    expect(outer).toHaveBeenCalledOnce()
  })
})

describe('键盘移动高亮', () => {
  it('向下移动到下一项', async () => {
    render()
    await open()

    await press('ArrowDown')

    expect(options()[1].classList.contains('active')).toBe(true)
  })

  it('在末项向下时循环回第一项', async () => {
    render({ modelValue: 'SKY' })
    await open()

    await press('ArrowDown')

    expect(options()[0].classList.contains('active')).toBe(true)
  })

  it('在首项向上时循环到末项', async () => {
    render()
    await open()

    await press('ArrowUp')

    expect(options()[2].classList.contains('active')).toBe(true)
  })

  it('Home 跳到第一项、End 跳到最后一项', async () => {
    render()
    await open()

    await press('End')
    expect(options()[2].classList.contains('active')).toBe(true)

    await press('Home')
    expect(options()[0].classList.contains('active')).toBe(true)
  })

  it('面板收起时 Home/End 不生效，避免误改选中项', async () => {
    const w = render()

    await press('End')

    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('鼠标划过某一项时把高亮跟过去', async () => {
    render()
    await open()

    options()[2].dispatchEvent(new MouseEvent('mousemove', { bubbles: true }))
    await nextTick()

    expect(options()[2].classList.contains('active')).toBe(true)
  })
})

describe('选中', () => {
  it('Enter 选中当前高亮项并收起面板', async () => {
    const w = render()
    await open()
    await press('ArrowDown')

    await press('Enter')

    expect(w.emitted('update:modelValue')?.[0]).toEqual(['VIOLET'])
    expect(panel()).toBeNull()
  })

  it('空格与 Enter 等效', async () => {
    const w = render()
    await open()
    await press('ArrowDown')

    await press(' ')

    expect(w.emitted('update:modelValue')?.[0]).toEqual(['VIOLET'])
  })

  it('收起状态下按 Enter 只展开面板，不误选', async () => {
    const w = render()

    await press('Enter')

    expect(panel()).not.toBeNull()
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('点击选项直接选中并收起', async () => {
    const w = render()
    await open()

    await options()[2].click()
    await nextTick()

    expect(w.emitted('update:modelValue')?.[0]).toEqual(['SKY'])
    expect(panel()).toBeNull()
  })

  it('选中项同时给勾号和 aria-selected，不靠颜色单独传达', async () => {
    render({ modelValue: 'VIOLET' })
    await open()

    expect(options()[1].querySelector('.sel-check')?.textContent).toBe('✓')
    expect(options()[1].getAttribute('aria-selected')).toBe('true')
    expect(options()[0].querySelector('.sel-check')?.textContent).toBe('')
  })

  it('支持数字与布尔取值（分组 id、上下架都用它）', async () => {
    const w = render({
      modelValue: 0,
      options: [
        { value: 0, label: '全部分组' },
        { value: 7, label: '订阅' },
      ],
    })
    await open()
    await press('ArrowDown')
    await press('Enter')

    expect(w.emitted('update:modelValue')?.[0]).toEqual([7])
  })
})

describe('边界情况', () => {
  it('选项为空时展开不报错，方向键也不越界', async () => {
    render({ options: [], modelValue: '' })

    await open()
    await press('ArrowDown')

    expect(options()).toHaveLength(0)
  })

  it('每个实例的面板 id 互不相同，aria 引用不会串到别的下拉上', async () => {
    render()
    await open()
    const firstId = panel()?.id

    wrapper?.unmount()
    document.body.innerHTML = ''

    render()
    await open()

    expect(panel()?.id).not.toBe(firstId)
  })
})
