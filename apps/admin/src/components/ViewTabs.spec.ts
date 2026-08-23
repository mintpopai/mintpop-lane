import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ViewTabs from "./ViewTabs.vue";

const AGENT_OPTIONS = [
  { value: "ALL", label: "全部", count: 2 },
  { value: "CLAUDE", label: "Claude Code", count: 1 },
  { value: "CODEX", label: "Codex", count: 1 },
];

function render(modelValue: string) {
  return mount(ViewTabs, {
    props: { modelValue, options: AGENT_OPTIONS, label: "按 Agent 分" },
  });
}

describe("ViewTabs", () => {
  it("当前一级同时用 active 类与 aria-current 标出来，不只靠颜色", () => {
    const tabs = render("CODEX").findAll(".admin-tab");

    expect(tabs[2].classes()).toContain("active");
    expect(tabs[2].attributes("aria-current")).toBe("true");
    expect(tabs[0].classes()).not.toContain("active");
    expect(tabs[0].attributes("aria-current")).toBeUndefined();
  });

  it("点某一类发出它的取值", async () => {
    const wrapper = render("ALL");

    await wrapper.findAll(".admin-tab")[1].trigger("click");

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("CLAUDE");
  });

  it("没给 count 的一级不渲染计数", () => {
    const wrapper = mount(ViewTabs, {
      props: {
        modelValue: "FRONT",
        options: [
          { value: "FRONT", label: "第一跳（出国）" },
          { value: "LAND", label: "第二跳（落地）" },
        ],
        label: "按跳数分",
      },
    });

    expect(wrapper.findAll(".admin-tab .fact")).toHaveLength(0);
  });

  it("这一层按什么分念给读屏听", () => {
    expect(render("ALL").get("nav").attributes("aria-label")).toBe("按 Agent 分");
  });
});
