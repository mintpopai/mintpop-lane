import { describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import LanePathGraphic from "./LanePathGraphic.vue";
import LaneVisual from "./LaneVisual.vue";

describe("LaneVisual", () => {
  it("整扇窗口是一个 role=img，读屏念一句总述", async () => {
    const { wrapper } = await mountWithI18n(LaneVisual);

    const win = wrapper.get(".window");
    expect(win.attributes("role")).toBe("img");
    expect(win.attributes("aria-label")).toBe(COPY.zh.ui.visual.ariaLabel);
  });

  it("窗口里嵌的链路卡走已接通形态，且收起文字标注（卡片放不下）", async () => {
    const { wrapper } = await mountWithI18n(LaneVisual);

    const path = wrapper.findComponent(LanePathGraphic);
    expect(path.props("variant")).toBe("active");
    expect(path.props("labels")).toBe(false);
  });

  it("终端井那两行取自 copy 的 terminal.lines，不在这里另写一份", async () => {
    const { wrapper } = await mountWithI18n(LaneVisual);

    const lines = COPY.zh.terminal.lines;
    const dim = lines.find((l) => l.kind === "dim")!.text;
    const out = lines.find((l) => l.kind === "out")!.text;
    const screen = wrapper.get(".well .screen").text();
    expect(screen).toContain(dim);
    expect(screen).toContain(out);
  });

  it("示例分配号是编出来的，不能把真实数据带上页面", async () => {
    const { wrapper } = await mountWithI18n(LaneVisual);

    expect(wrapper.get(".chip").text()).toBe("7K3M9-QX2FT");
  });

  it("英文页整扇窗口换英文", async () => {
    const { wrapper } = await mountWithI18n(LaneVisual, { locale: "en" });

    expect(wrapper.get(".window").attributes("aria-label")).toBe(COPY.en.ui.visual.ariaLabel);
    expect(wrapper.get(".card-head h3").text()).toBe(COPY.en.ui.visual.cardTitle);
  });
});
