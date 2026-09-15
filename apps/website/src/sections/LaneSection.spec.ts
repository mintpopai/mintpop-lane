import { describe, expect, it } from "vitest";
import LanePathGraphic from "../components/LanePathGraphic.vue";
import { COPY } from "../content/copy";
import { mountWithI18n } from "../testing";
import LaneSection from "./LaneSection.vue";

describe("LaneSection", () => {
  it("这一节的图用放大版、带文字标注，讲的是链路通的样子", async () => {
    const { wrapper } = await mountWithI18n(LaneSection);

    const path = wrapper.findComponent(LanePathGraphic);
    expect(path.props("variant")).toBe("active");
    expect(path.props("size")).toBe("lg");
    // 这里是正文里的主图，标注必须在
    expect(path.props("labels")).toBe(true);
  });

  it("图下的说明与要点网格都来自文案", async () => {
    const { wrapper } = await mountWithI18n(LaneSection);

    expect(wrapper.get(".caption").text()).toBe(COPY.zh.ui.lane.caption);
    expect(wrapper.findAll(".cell h3").map((h) => h.text())).toEqual(
      COPY.zh.lane.points.map((p) => p.title),
    );
  });

  it("英文页换英文文案，要点条数与中文一致", async () => {
    const { wrapper } = await mountWithI18n(LaneSection, { locale: "en" });

    expect(wrapper.get(".caption").text()).toBe(COPY.en.ui.lane.caption);
    expect(wrapper.findAll(".cell")).toHaveLength(COPY.zh.lane.points.length);
  });
});
