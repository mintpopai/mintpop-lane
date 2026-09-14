import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import GateShell from "./GateShell.vue";

describe("GateShell", () => {
  it("品牌标识与站点地址是 page chrome，登录卡只放调用方通过 slot 给的内容", () => {
    const wrapper = mount(GateShell, {
      slots: { default: '<h1 class="gate-title">管理员登录</h1>' },
    });

    expect(wrapper.get(".gate-main .gate-brand-text").text()).toBe("Lane 管理后台");
    expect(wrapper.get(".gate-main .gate-foot a").attributes("href")).toBe(
      "https://lane.mintpop.ai",
    );
    expect(wrapper.get(".gate-box").text()).toBe("管理员登录");
  });

  // 三处各说一样东西，都不重复：左上角只有产品名、卡里是产品图标、带子里是母品牌标记
  it("左上角只写产品名，不挂任何图形标识", () => {
    const wrapper = mount(GateShell);

    expect(wrapper.find(".gate-brand img").exists()).toBe(false);
    expect(wrapper.find(".gate-wordmark").exists()).toBe(false);
  });

  it("带子里是 MintPop 组织标记与品牌 slogan", () => {
    const wrapper = mount(GateShell);
    const avatar = wrapper.get(".gate-panel .gate-avatar");

    expect(avatar.attributes("src")).toContain("brand/avatar.png");
    expect(avatar.attributes("alt")).toBe("MintPop");
    // 标了尺寸，图没到之前不抖版
    expect(avatar.attributes("width")).toBe("64");
    expect(avatar.attributes("height")).toBe("64");
    expect(wrapper.get(".gate-panel .gate-slogan").text()).toBe("Pop into something fresh");
  });

  it("卡片默认窄、wide 时放宽一档", () => {
    expect(mount(GateShell).get(".gate-box").classes()).not.toContain("gate-box-wide");
    expect(mount(GateShell, { props: { wide: true } }).get(".gate-box").classes()).toContain(
      "gate-box-wide",
    );
  });

  it("气泡场是纯装饰：不进无障碍树，也不含任何文案", () => {
    const bubbles = mount(GateShell).get(".gate-bubbles");

    expect(bubbles.attributes("aria-hidden")).toBe("true");
    expect(bubbles.text()).toBe("");
    // 16 枚：CSS 按 nth-child 逐枚分配位置/尺寸/节奏，数量改了要同步改样式
    expect(bubbles.findAll(".gate-bubble")).toHaveLength(16);
  });

  // 气泡场铺满整条带子当背景，品牌块压在它上层——这样气泡是从标记与 slogan 身后升上来的
  it("品牌块与气泡场是分开的两层", () => {
    const panel = mount(GateShell).get(".gate-panel");

    expect(panel.find(".gate-brand-block .gate-avatar").exists()).toBe(true);
    expect(panel.find(".gate-brand-block .gate-slogan").exists()).toBe(true);
    expect(panel.find(".gate-brand-block .gate-bubble").exists()).toBe(false);
  });

  it("闸门页不写宣传语：带子里除 slogan 外没有别的文案", () => {
    expect(mount(GateShell).get(".gate-panel").text()).toBe("Pop into something fresh");
  });
});
