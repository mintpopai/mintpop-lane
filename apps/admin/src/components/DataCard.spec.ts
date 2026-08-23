import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DataCard from "./DataCard.vue";

const SLOTS = {
  default: "<table class='admin-table'><tbody><tr><td>一行数据</td></tr></tbody></table>",
  "empty-action": "<button>新建套餐</button>",
};

describe("DataCard", () => {
  it("四态都在同一张卡里换，卡框恒在", () => {
    const states = [
      mount(DataCard, { props: { loading: true }, slots: SLOTS }),
      mount(DataCard, { props: { error: "服务不可用" }, slots: SLOTS }),
      mount(DataCard, { props: { empty: true, emptyText: "还没有套餐。" }, slots: SLOTS }),
      mount(DataCard, { slots: SLOTS }),
    ];

    for (const wrapper of states) {
      expect(wrapper.find(".admin-card").exists()).toBe(true);
    }
  });

  it("加载中压过错误与空态，不会两态同时出现", () => {
    const wrapper = mount(DataCard, {
      props: { loading: true, error: "服务不可用", empty: true, emptyText: "还没有套餐。" },
      slots: SLOTS,
    });

    expect(wrapper.get(".card-state").text()).toBe("加载中……");
    expect(wrapper.find(".card-state.error").exists()).toBe(false);
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("错误文案直接用服务端给的提示，并标成 alert", () => {
    const wrapper = mount(DataCard, { props: { error: "410003 节点仍被引用" }, slots: SLOTS });

    const state = wrapper.get(".card-state.error");
    expect(state.text()).toBe("410003 节点仍被引用");
    expect(state.attributes("role")).toBe("alert");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("空态给出说明与下一步动作", () => {
    const wrapper = mount(DataCard, {
      props: { empty: true, emptyText: "还没有套餐。" },
      slots: SLOTS,
    });

    expect(wrapper.get(".card-state-text").text()).toBe("还没有套餐。");
    expect(wrapper.get(".card-state-actions button").text()).toBe("新建套餐");
    expect(wrapper.find("table").exists()).toBe(false);
  });

  it("有数据时只渲染表格，空态的动作插槽不跟着露出来", () => {
    const wrapper = mount(DataCard, { slots: SLOTS });

    expect(wrapper.find("table").exists()).toBe(true);
    expect(wrapper.find(".card-state").exists()).toBe(false);
    expect(wrapper.find(".card-state-actions").exists()).toBe(false);
  });
});
