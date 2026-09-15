import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import AppLayout from "./AppLayout.vue";

describe("AppLayout", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("导航轨顶部是闸门页那套品牌锁定组合：字标 + Lane + 管理后台", () => {
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
    // 产品名与身份说明分两层：Lane 是品牌锁定组合的一部分，「管理后台」只是注脚
    expect(wrapper.get(".rail-brand-text").text().replace(/\s+/g, " ")).toBe("Lane 管理后台");
    expect(wrapper.get(".rail-kind").text()).toBe("管理后台");
  });
});
