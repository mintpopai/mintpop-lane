import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import AppLayout from "./AppLayout.vue";

describe("AppLayout", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("导航轨顶部用 wordmark 图片做品牌字标，副题保留「Lane 管理后台」", () => {
    const wrapper = mount(AppLayout, {
      // 导航链接与工作区是真路由的事，这里桩掉即可
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
          RouterView: { template: "<div />" },
        },
      },
    });

    expect(wrapper.get(".rail-wordmark").attributes("src")).toContain(
      "brand/wordmark/mintpop-wordmark-dark.png",
    );
    expect(wrapper.get(".rail-wordmark").attributes("alt")).toBe("MintPop");
    expect(wrapper.get(".rail-kind").text()).toBe("Lane 管理后台");
  });
});
