import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import GateShell from "./GateShell.vue";

describe("GateShell", () => {
  it("品牌标识与站点地址是 page chrome，登录卡只放调用方通过 slot 给的内容", () => {
    const wrapper = mount(GateShell, {
      slots: { default: '<h1 class="gate-title">管理员登录</h1>' },
    });

    expect(wrapper.get(".gate-main .gate-brand-text").text()).toBe("Lane 管理后台");
    // 「管理后台」是身份说明、不是产品名的一部分，单独一档小灰字
    expect(wrapper.get(".gate-main .gate-brand-kind").text()).toBe("管理后台");
    expect(wrapper.get(".gate-main .gate-foot a").attributes("href")).toBe(
      "https://lane.mintpop.ai",
    );
    expect(wrapper.get(".gate-box").text()).toBe("管理员登录");
  });

  // 与官网首页顶栏同一套品牌锁定组合：瓦片 + 官方词标图 + 产品名。品牌部分一律用词标图、
  // 不用文字排 logo（品牌规范 INVARIANT），所以这里断言的是 <img> 而不是一串文字
  it("左上角是官网首页那套品牌锁定组合：应用瓦片 + 官方词标 + 产品名", () => {
    const wrapper = mount(GateShell);
    const icon = wrapper.get(".gate-brand .gate-brand-icon");
    const wordmark = wrapper.get(".gate-brand .gate-brand-wordmark");

    expect(icon.attributes("src")).toContain("products/lane/lane-app-cloud.png");
    // 瓦片与紧随其后的词标说的是同一件事，读屏念两遍反而啰嗦
    expect(icon.attributes("alt")).toBe("");
    expect(wordmark.attributes("src")).toContain("brand/wordmark/mintpop-wordmark-dark.png");
    expect(wordmark.attributes("alt")).toBe("MintPop");
    // 标了尺寸，图没到之前不抖版
    expect(icon.attributes("width")).toBe("36");
    expect(icon.attributes("height")).toBe("36");
    expect(wordmark.attributes("width")).toBe("106");
    expect(wordmark.attributes("height")).toBe("29");
  });

  it("带子里是 MintPop 组织标记与品牌 slogan", () => {
    const wrapper = mount(GateShell);
    const avatar = wrapper.get(".gate-panel .gate-avatar");

    expect(avatar.attributes("src")).toContain("brand/avatar.png");
    expect(avatar.attributes("alt")).toBe("MintPop");
    // 标了尺寸，图没到之前不抖版
    expect(avatar.attributes("width")).toBe("80");
    expect(avatar.attributes("height")).toBe("80");
    expect(wrapper.get(".gate-panel .gate-slogan").text()).toBe("Pop into something fresh");
  });

  // slogan 是逐字打出来的，但「打」只是视觉：整句任何时候都完整地在 DOM 里，
  // 读屏与复制拿到的是完整品牌语，不受动画进度影响
  it("slogan 演打字机：真身整句始终可读，打字层逐字推进且不进无障碍树", async () => {
    vi.useFakeTimers();

    try {
      const wrapper = mount(GateShell);
      const real = wrapper.get(".gate-panel .gate-slogan-real");
      const typed = wrapper.get(".gate-panel .gate-slogan-typed");

      expect(real.text()).toBe("Pop into something fresh");
      // 同一句话若两层都进无障碍树，读屏会念两遍
      expect(typed.attributes("aria-hidden")).toBe("true");
      // 起打前打字层是空的（末尾那枚光标不含文字）
      expect(typed.text()).toBe("");

      // 640ms 起打、每字 46ms：推进三个字符的时间，正好打出 "Pop"
      await vi.advanceTimersByTimeAsync(640 + 46 * 3);
      expect(typed.text()).toBe("Pop");

      // 再往后推到整句打完，计时器自己停住，不会越过整句继续切片
      await vi.advanceTimersByTimeAsync(46 * 40);
      expect(typed.text()).toBe("Pop into something fresh");
    } finally {
      vi.useRealTimers();
    }
  });

  // 动效敏感的人不该被逐字动画伺候：这一档直接把整句给到，光标由 CSS 撤掉
  it("prefers-reduced-motion 档不演打字，挂载即给全文", async () => {
    const matchMedia = vi.fn().mockReturnValue({ matches: true });
    vi.stubGlobal("matchMedia", matchMedia);

    try {
      const wrapper = mount(GateShell);
      // 全文是在 onMounted 里一次性写进 ref 的，等一帧让它落到 DOM 上
      await wrapper.vm.$nextTick();

      expect(matchMedia).toHaveBeenCalledWith("(prefers-reduced-motion: reduce)");
      expect(wrapper.get(".gate-panel .gate-slogan-typed").text()).toBe(
        "Pop into something fresh",
      );
    } finally {
      vi.unstubAllGlobals();
    }
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

  // 带子里的文字只有 slogan 一句：打字层此刻还没起打（是空的），
  // 所以整条带子的文本就是真身那一句
  it("闸门页不写宣传语：带子里除 slogan 外没有别的文案", () => {
    expect(mount(GateShell).get(".gate-panel").text()).toBe("Pop into something fresh");
  });
});
