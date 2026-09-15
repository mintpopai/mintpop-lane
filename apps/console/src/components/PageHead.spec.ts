import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import PageHead from "./PageHead.vue";

describe("PageHead", () => {
  it("有页面级操作时才切到两栏布局", () => {
    const withActions = mount(PageHead, {
      props: { title: "套餐" },
      slots: { actions: "<button>新建套餐</button>" },
    });
    const withoutActions = mount(PageHead, { props: { title: "用户" } });

    expect(withActions.get("header").classes()).toContain("with-actions");
    expect(withoutActions.get("header").classes()).not.toContain("with-actions");
  });

  it("没有 actions 插槽时不渲染操作区，避免留一块空盒子", () => {
    const wrapper = mount(PageHead, { props: { title: "用户" } });

    expect(wrapper.find(".page-head-actions").exists()).toBe(false);
  });

  it("标题与规模事实各就各位", () => {
    const wrapper = mount(PageHead, {
      props: { title: "节点池" },
      slots: { facts: "共 12 个节点" },
    });

    expect(wrapper.get(".page-title").text()).toBe("节点池");
    expect(wrapper.get(".page-facts").text()).toBe("共 12 个节点");
  });
});
