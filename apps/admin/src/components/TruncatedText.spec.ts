import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TruncatedText from "./TruncatedText.vue";

/**
 * jsdom 不做布局，scrollWidth / clientWidth 恒为 0，「有没有被截断」测不出来。
 * 这里把这两个量按用例需要钉死，模拟「文本比格子宽」与「放得下」两种真实情形。
 */
function stubWidths(el: Element, scrollWidth: number, clientWidth: number): void {
  Object.defineProperty(el, "scrollWidth", { value: scrollWidth, configurable: true });
  Object.defineProperty(el, "clientWidth", { value: clientWidth, configurable: true });
}

/**
 * 不传 attachTo：VTU 会为它自建一个 <div data-v-app> 挂载容器，
 * wrapper.element 就指到那个 div 而不是组件根，宽度桩会打歪。
 * 气泡自己 Teleport 到 body，本来也不需要挂进真实 DOM。
 */
function mountText(text: string | null) {
  return mount(TruncatedText, { props: { text } });
}

/** 气泡 Teleport 到 body，组件 wrapper 里找不到，须从 document 上取 */
function tip(): DOMWrapper<Element> | null {
  const el = document.querySelector('[role="tooltip"]');
  return el ? new DOMWrapper(el) : null;
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  document.body.innerHTML = "";
});

describe("TruncatedText", () => {
  it("文本放得下就不弹气泡——再弹一个一模一样的内容纯属噪声", async () => {
    const wrapper = mountText("老客户");
    stubWidths(wrapper.element, 80, 220);

    await wrapper.trigger("mouseenter");
    vi.advanceTimersByTime(1000);
    await wrapper.vm.$nextTick();

    expect(tip()).toBeNull();
  });

  it("被截断时悬浮弹出完整原文", async () => {
    const full = "管理员".repeat(16);
    const wrapper = mountText(full);
    stubWidths(wrapper.element, 600, 220);

    await wrapper.trigger("mouseenter");
    vi.advanceTimersByTime(200);
    await wrapper.vm.$nextTick();

    expect(tip()?.text()).toBe(full);
  });

  it("不立刻弹：鼠标扫过一列时不该一路闪", async () => {
    const wrapper = mountText("管理员".repeat(16));
    stubWidths(wrapper.element, 600, 220);

    await wrapper.trigger("mouseenter");
    await wrapper.vm.$nextTick();
    expect(tip()).toBeNull();

    vi.advanceTimersByTime(200);
    await wrapper.vm.$nextTick();
    expect(tip()).not.toBeNull();
  });

  it("延迟没到就移开，气泡不该再冒出来", async () => {
    const wrapper = mountText("管理员".repeat(16));
    stubWidths(wrapper.element, 600, 220);

    await wrapper.trigger("mouseenter");
    vi.advanceTimersByTime(50);
    await wrapper.trigger("mouseleave");
    vi.advanceTimersByTime(1000);
    await wrapper.vm.$nextTick();

    expect(tip()).toBeNull();
  });

  it("移开立即消失，不拖泥带水", async () => {
    const wrapper = mountText("管理员".repeat(16));
    stubWidths(wrapper.element, 600, 220);

    await wrapper.trigger("mouseenter");
    vi.advanceTimersByTime(200);
    await wrapper.vm.$nextTick();
    expect(tip()).not.toBeNull();

    await wrapper.trigger("mouseleave");
    await wrapper.vm.$nextTick();
    expect(tip()).toBeNull();
  });

  it("键盘聚焦同样能看全文——原生 title 键盘用户根本摸不到", async () => {
    const full = "管理员".repeat(16);
    const wrapper = mountText(full);
    stubWidths(wrapper.element, 600, 220);

    expect(wrapper.attributes("tabindex")).toBe("0");

    await wrapper.trigger("focus");
    vi.advanceTimersByTime(200);
    await wrapper.vm.$nextTick();

    expect(tip()?.text()).toBe(full);
  });

  it("没有内容时渲染占位符，且不可聚焦——没东西可看就别挡在 Tab 路上", async () => {
    const wrapper = mountText(null);

    expect(wrapper.text()).toBe("—");
    expect(wrapper.attributes("tabindex")).toBeUndefined();
  });

  it("组件卸载时把挂在 window 上的跟随监听收掉", async () => {
    const remove = vi.spyOn(window, "removeEventListener");
    const wrapper = mountText("管理员".repeat(16));
    stubWidths(wrapper.element, 600, 220);
    await wrapper.trigger("mouseenter");
    vi.advanceTimersByTime(200);

    wrapper.unmount();

    expect(remove).toHaveBeenCalledWith("scroll", expect.any(Function), true);
    expect(remove).toHaveBeenCalledWith("resize", expect.any(Function));
  });
});
