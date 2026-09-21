import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AppLayout from "./AppLayout.vue";
import { useRebindStore } from "../stores/rebind";

vi.mock("../api", () => ({ adminApi: () => ({ listDeviceRebindRequests: async () => [] }) }));

describe("AppLayout", () => {
  beforeEach(() => setActivePinia(createPinia()));

  function render() {
    return mount(AppLayout, {
      // 导航链接与工作区是真路由的事，这里桩掉即可
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
          RouterView: { template: "<div />" },
        },
      },
    });
  }

  it("导航轨顶部是闸门页那套品牌锁定组合：字标 + Lane + 管理后台", () => {
    const wrapper = render();

    expect(wrapper.get(".rail-wordmark").attributes("src")).toContain(
      "brand/wordmark/mintpop-wordmark-dark.png",
    );
    expect(wrapper.get(".rail-wordmark").attributes("alt")).toBe("MintPop");
    // 产品名与身份说明分两层：Lane 是品牌锁定组合的一部分，「管理后台」只是注脚
    expect(wrapper.get(".rail-brand-text").text().replace(/\s+/g, " ")).toBe("Lane 管理后台");
    expect(wrapper.get(".rail-kind").text()).toBe("管理后台");
  });

  it("导航轨有「换机申请」入口", () => {
    const wrapper = render();

    expect(wrapper.findAll(".rail-link").map((el) => el.text())).toContain("换机申请");
  });

  it("导航轨有「链路健康」入口", () => {
    const wrapper = render();

    expect(wrapper.findAll(".rail-link").map((el) => el.text())).toContain("链路健康");
  });

  it("没有待办时不画角标，有待办时画出数字", async () => {
    const wrapper = render();

    expect(wrapper.find(".rail-badge").exists()).toBe(false);

    useRebindStore().pendingCount = 3;
    await wrapper.vm.$nextTick();

    expect(wrapper.get(".rail-badge").text()).toBe("3");
  });
});
