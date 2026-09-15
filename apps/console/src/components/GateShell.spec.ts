import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import GateShell from "./GateShell.vue";

describe("GateShell", () => {
  it("左上角品牌行读作「Lane 控制台」：Lane 是产品名，「控制台」是身份说明", () => {
    const wrapper = mount(GateShell, {
      slots: { default: '<h1 class="gate-title">统一登录</h1>' },
    });
    expect(wrapper.get(".gate-main .gate-brand-text").text().replace(/\s+/g, " ")).toBe(
      "Lane 控制台",
    );
    expect(wrapper.get(".gate-main .gate-brand-kind").text()).toBe("控制台");
    expect(wrapper.get(".gate-main .gate-foot a").attributes("href")).toBe(
      "https://lane.mintpop.ai",
    );
    expect(wrapper.get(".gate-box").text()).toBe("统一登录");
  });

  it("品牌锁定组合用官方瓦片与词标图，标了尺寸不抖版", () => {
    const wrapper = mount(GateShell);
    const icon = wrapper.get(".gate-brand .gate-brand-icon");
    const wordmark = wrapper.get(".gate-brand .gate-brand-wordmark");
    expect(icon.attributes("src")).toContain("products/lane/lane-app-cloud.png");
    expect(icon.attributes("alt")).toBe("");
    expect(wordmark.attributes("src")).toContain("brand/wordmark/mintpop-wordmark-dark.png");
    expect(wordmark.attributes("width")).toBe("106");
  });

  it("带子里是 MintPop 组织标记与 slogan；气泡场 16 枚且不进无障碍树", () => {
    const wrapper = mount(GateShell);
    expect(wrapper.get(".gate-panel .gate-avatar").attributes("alt")).toBe("MintPop");
    expect(wrapper.get(".gate-panel .gate-slogan-real").text()).toBe("Pop into something fresh");
    const bubbles = wrapper.get(".gate-bubbles");
    expect(bubbles.attributes("aria-hidden")).toBe("true");
    expect(bubbles.findAll(".gate-bubble")).toHaveLength(16);
  });

  it("slogan 打字机：真身整句始终可读，打字层逐字推进", async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(GateShell);
      const typed = wrapper.get(".gate-panel .gate-slogan-typed");
      expect(typed.attributes("aria-hidden")).toBe("true");
      expect(typed.text()).toBe("");
      await vi.advanceTimersByTimeAsync(640 + 46 * 3);
      expect(typed.text()).toBe("Pop");
      await vi.advanceTimersByTimeAsync(46 * 40);
      expect(typed.text()).toBe("Pop into something fresh");
    } finally {
      vi.useRealTimers();
    }
  });

  it("prefers-reduced-motion 档挂载即给全文", async () => {
    vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({ matches: true }));
    try {
      const wrapper = mount(GateShell);
      await wrapper.vm.$nextTick();
      expect(wrapper.get(".gate-panel .gate-slogan-typed").text()).toBe("Pop into something fresh");
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("卡片默认窄、wide 时放宽一档", () => {
    expect(mount(GateShell).get(".gate-box").classes()).not.toContain("gate-box-wide");
    expect(
      mount(GateShell, { props: { wide: true } })
        .get(".gate-box")
        .classes(),
    ).toContain("gate-box-wide");
  });
});
