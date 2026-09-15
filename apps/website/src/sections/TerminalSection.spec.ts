import { describe, expect, it } from "vitest";
import TerminalMock from "../components/TerminalMock.vue";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import TerminalSection from "./TerminalSection.vue";

describe("TerminalSection", () => {
  it("侧栏示例会话里有且只有一个是当前会话", async () => {
    const { wrapper } = await mountWithI18n(TerminalSection);

    const tabs = wrapper.findComponent(TerminalMock).props("tabs")!;
    expect(tabs.filter((t) => t.active)).toHaveLength(1);
    expect(tabs[0]).toMatchObject({ name: "lane-website", active: true });
  });

  it("要点列表来自文案", async () => {
    const { wrapper } = await mountWithI18n(TerminalSection);

    expect(wrapper.findAll(".points li h3").map((h) => h.text())).toEqual(
      COPY.zh.terminal.points.map((p) => p.title),
    );
  });

  it("英文页换英文文案", async () => {
    const { wrapper } = await mountWithI18n(TerminalSection, { locale: "en" });

    expect(wrapper.get(".title").text()).toBe(COPY.en.terminal.title);
    expect(wrapper.findAll(".points li")).toHaveLength(COPY.zh.terminal.points.length);
  });
});
