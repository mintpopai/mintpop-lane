import { describe, expect, it } from "vitest";
import { COPY, type LaneVariant } from "../content/copy";
import { mountWithI18n } from "../testing";
import LanePathGraphic from "./LanePathGraphic.vue";

/** 三个节点的标记类 + 两段连线的形态类，即这张图「说了什么」 */
async function shapeOf(variant: LaneVariant) {
  const { wrapper } = await mountWithI18n(LanePathGraphic, { props: { variant } });
  return {
    nodes: wrapper.findAll(".mark").map((m) => m.classes().filter((c) => c !== "mark")),
    segs: wrapper.findAll(".seg").map((s) => s.classes().filter((c) => c !== "seg")),
    glyphs: wrapper.findAll(".mark").map((m) => m.text()),
  };
}

describe("LanePathGraphic", () => {
  it("五种处境各有各的形状，与桌面端 present 表逐一对齐", async () => {
    expect((await shapeOf("active")).nodes).toEqual([["ok"], ["ok"], ["ok"]]);
    expect((await shapeOf("connecting")).segs).toEqual([["on"], ["flow"]]);
    expect((await shapeOf("unreachable")).nodes).toEqual([["ok"], ["fail"], ["off"]]);
    expect((await shapeOf("unreachable")).segs).toEqual([["broken"], ["off"]]);
    expect((await shapeOf("mismatch")).nodes).toEqual([["ok"], ["ok"], ["warn"]]);
    expect((await shapeOf("off")).nodes).toEqual([["ok"], ["off"], ["off"]]);
  });

  it("状态不靠颜色单独传达：出错与告警各配一个字形", async () => {
    expect((await shapeOf("unreachable")).glyphs).toEqual(["✓", "✕", ""]);
    expect((await shapeOf("mismatch")).glyphs).toEqual(["✓", "✓", "!"]);
  });

  it("整张图是一个 role=img，读屏念一句话，不逐个念节点", async () => {
    const { wrapper } = await mountWithI18n(LanePathGraphic, { props: { variant: "unreachable" } });

    const img = wrapper.get(".lane");
    expect(img.attributes("role")).toBe("img");
    expect(img.attributes("aria-label")).toBe(COPY.zh.ui.path.aria.unreachable);
    // 字形是装饰，已由 aria-label 说清，不该被读屏重复念一遍
    expect(wrapper.findAll(".mark").every((m) => m.attributes("aria-hidden") === "true")).toBe(
      true,
    );
  });

  it("aria 文案随语言走", async () => {
    const { wrapper } = await mountWithI18n(LanePathGraphic, {
      locale: "en",
      props: { variant: "active" },
    });

    expect(wrapper.get(".lane").attributes("aria-label")).toBe(COPY.en.ui.path.aria.active);
  });

  it("三个节点配三条说明；labels 关掉时只剩形状，供嵌进小卡片用", async () => {
    const withLabels = await mountWithI18n(LanePathGraphic, { props: { labels: true } });
    expect(withLabels.wrapper.findAll(".name").map((n) => n.text())).toEqual(COPY.zh.ui.path.names);
    expect(withLabels.wrapper.findAll(".hint")).toHaveLength(3);
    expect(withLabels.wrapper.get(".lane").classes()).not.toContain("bare");

    const bare = await mountWithI18n(LanePathGraphic, { props: { labels: false } });
    expect(bare.wrapper.findAll(".name")).toHaveLength(0);
    expect(bare.wrapper.get(".lane").classes()).toContain("bare");
    // 形状还在：关掉的只是文字
    expect(bare.wrapper.findAll(".mark")).toHaveLength(3);
  });

  it("默认是 active + md，两段连线把三个节点串起来", async () => {
    const { wrapper } = await mountWithI18n(LanePathGraphic);

    expect(wrapper.get(".lane").classes()).toContain("md");
    expect(wrapper.findAll(".mark")).toHaveLength(3);
    expect(wrapper.findAll(".seg")).toHaveLength(2);
  });
});
