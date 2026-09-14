import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import GateShell from "./GateShell.vue";

describe("GateShell", () => {
  it("左侧品牌面板固定文案，右侧渲染调用方通过 slot 放进来的内容", () => {
    const wrapper = mount(GateShell, {
      slots: { default: '<h1 class="gate-title">用管理员账号登录</h1>' },
    });

    expect(wrapper.get(".gate-panel .gate-kicker").text()).toBe("Lane 管理后台");
    expect(wrapper.get(".gate-panel .gate-foot a").attributes("href")).toBe(
      "https://lane.mintpop.ai",
    );
    expect(wrapper.get(".gate-box .gate-title").text()).toBe("用管理员账号登录");
  });

  it("品牌行用 lane 产品图标 + wordmark 图片，装饰图不带 alt 文案", () => {
    const wrapper = mount(GateShell);

    expect(wrapper.get(".gate-icon").attributes("src")).toContain("products/lane/lane-app-cloud.png");
    expect(wrapper.get(".gate-icon").attributes("alt")).toBe("");
    expect(wrapper.get(".gate-wordmark").attributes("src")).toContain("mintpop-wordmark-dark.png");
    expect(wrapper.get(".gate-wordmark").attributes("alt")).toBe("MintPop");
    expect(wrapper.get(".gate-brand-text").text()).toContain("Lane 管理后台");
  });
});
