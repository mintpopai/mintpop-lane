import { describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import TerminalMock from "./TerminalMock.vue";

const TABS = [{ name: "lane-website", active: true }, { name: "api-server" }] as const;

describe("TerminalMock", () => {
  it("整块是一个 role=img：它是示意图，不是可操作的终端", async () => {
    const { wrapper } = await mountWithI18n(TerminalMock);

    const well = wrapper.get(".well");
    expect(well.attributes("role")).toBe("img");
    expect(well.attributes("aria-label")).toBe(COPY.zh.ui.terminalMock.ariaLabel);
  });

  it("给了 tabs 才渲染侧栏，当前会话单独标出来", async () => {
    const { wrapper } = await mountWithI18n(TerminalMock, { props: { tabs: TABS } });

    const tabs = wrapper.findAll(".tab");
    expect(tabs.map((t) => t.get(".tab-name").text())).toEqual(["lane-website", "api-server"]);
    expect(tabs.filter((t) => t.classes().includes("on"))).toHaveLength(1);
    expect(tabs[0].classes()).toContain("on");
  });

  it("不给 tabs 就整条侧栏都不渲染，不留一条空栏", async () => {
    const { wrapper } = await mountWithI18n(TerminalMock);

    expect(wrapper.find(".rail").exists()).toBe(false);
  });

  it("chrome 关掉时不画 macOS 交通灯，供嵌进别的窗口里用", async () => {
    const withChrome = await mountWithI18n(TerminalMock);
    expect(withChrome.wrapper.find(".bar").exists()).toBe(true);

    const bare = await mountWithI18n(TerminalMock, { props: { chrome: false } });
    expect(bare.wrapper.find(".bar").exists()).toBe(false);
    // 屏幕内容不受影响
    expect(bare.wrapper.find(".screen").exists()).toBe(true);
  });

  it("屏幕内容按 kind 分别排版：提示符、暗行、输出、光标行", async () => {
    const { wrapper } = await mountWithI18n(TerminalMock);
    const screen = wrapper.get(".screen");

    // 每种 kind 至少出现一次，且暗行用 .dim、光标行用 .caret-line
    expect(screen.findAll(".dim").length).toBeGreaterThan(0);
    expect(screen.find(".caret-line").exists()).toBe(true);
    expect(screen.findAll(".sigil").length).toBeGreaterThan(0);

    // 文案里的每一行都真的出现在屏幕上（光标行只有符号、无文本）
    for (const line of COPY.zh.terminal.lines) {
      if (line.kind !== "cursor") expect(screen.text()).toContain(line.text);
    }
  });

  it("英文页换英文的 aria 与状态字", async () => {
    const { wrapper } = await mountWithI18n(TerminalMock, { locale: "en" });

    expect(wrapper.get(".well").attributes("aria-label")).toBe(COPY.en.ui.terminalMock.ariaLabel);
    expect(wrapper.get(".state").text()).toContain(COPY.en.ui.terminalMock.state);
  });
});
