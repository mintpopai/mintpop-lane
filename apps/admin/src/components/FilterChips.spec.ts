import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import FilterChips from "./FilterChips.vue";

const STATUS_OPTIONS = [
  { value: "ALL", label: "全部", count: 9 },
  { value: "ENABLED", label: "上架", count: 7, state: "ENABLED" },
  { value: "DISABLED", label: "停用", count: 2, state: "DISABLED" },
];

function render(modelValue: string) {
  return mount(FilterChips, {
    props: { modelValue, options: STATUS_OPTIONS, label: "按上架状态筛选" },
  });
}

describe("FilterChips", () => {
  it("当前批次同时用 active 类与 aria-pressed 标出来，不只靠颜色", () => {
    const chips = render("ENABLED").findAll(".admin-chip");

    expect(chips[1].classes()).toContain("active");
    expect(chips[1].attributes("aria-pressed")).toBe("true");
    expect(chips[0].classes()).not.toContain("active");
    expect(chips[0].attributes("aria-pressed")).toBe("false");
  });

  it("点某一批发出它的取值", async () => {
    const wrapper = render("ALL");

    await wrapper.findAll(".admin-chip")[2].trigger("click");

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toBe("DISABLED");
  });

  it("有状态语义的批次才带色点，「全部」不带", () => {
    const chips = render("ALL").findAll(".admin-chip");

    expect(chips[0].attributes("data-state")).toBeUndefined();
    expect(chips[1].attributes("data-state")).toBe("ENABLED");
  });

  it("没给 count 的选项不渲染计数（服务端分页的页面就是这种）", () => {
    const wrapper = mount(FilterChips, {
      props: {
        modelValue: null as boolean | null,
        options: [
          { value: null, label: "全部" },
          { value: true, label: "有在期订阅" },
        ],
        label: "按有无在期订阅筛选",
      },
    });

    expect(wrapper.findAll(".admin-chip .fact")).toHaveLength(0);
    expect(wrapper.findAll(".admin-chip")[0].text()).toBe("全部");
  });

  it("维度说明念给读屏听，界面上不显示", () => {
    const group = render("ALL").get(".chip-group");

    expect(group.attributes("role")).toBe("group");
    expect(group.attributes("aria-label")).toBe("按上架状态筛选");
  });
});
