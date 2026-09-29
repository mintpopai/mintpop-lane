import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, type Router } from "vue-router";
import AppLayout from "./AppLayout.vue";
import { useRebindStore } from "../stores/rebind";
import { createAppRouter } from "../router";

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

  it("导航轨有「全局配置」入口", () => {
    const wrapper = render();

    expect(wrapper.findAll(".rail-link").map((el) => el.text())).toContain("全局配置");
  });

  it("没有待办时不画角标，有待办时画出数字", async () => {
    const wrapper = render();

    expect(wrapper.find(".rail-badge").exists()).toBe(false);

    useRebindStore().pendingCount = 3;
    await wrapper.vm.$nextTick();

    expect(wrapper.get(".rail-badge").text()).toBe("3");
  });
});

describe("AppLayout 线路分组", () => {
  beforeEach(() => setActivePinia(createPinia()));

  /** 生产路由表 + 管理员身份：高亮规则取决于路由怎么嵌套，手抄一份路由表测不出回归 */
  function adminRouter() {
    return createAppRouter(
      { me: async () => ({ id: 1, email: "a@b.c", role: "ADMIN", subscriptions: [] }) },
      createMemoryHistory(),
    );
  }

  /** 工作区是具体页面的事，桩掉，只看侧栏 */
  function mountWithRouter(router: Router) {
    return mount(AppLayout, {
      global: { plugins: [router], stubs: { RouterView: { template: "<div />" } } },
    });
  }

  it("「线路」是分组标题，下面是机场订阅、落地节点；不再有独立的「机场」「节点池」", () => {
    const wrapper = mount(AppLayout, {
      global: {
        stubs: { RouterLink: { template: "<a><slot /></a>" }, RouterView: { template: "<div />" } },
      },
    });

    expect(wrapper.get(".rail-group-title").text()).toBe("线路");
    expect(wrapper.findAll(".rail-group .rail-link").map((el) => el.text())).toEqual([
      "机场订阅",
      "落地节点",
    ]);
    const links = wrapper.findAll(".rail-link").map((el) => el.text());
    expect(links).not.toContain("机场");
    expect(links).not.toContain("节点池");
  });

  it("在订阅详情页时，侧栏仍高亮「机场订阅」（用生产路由表，防止详情路由又被拆成平级）", async () => {
    const router = adminRouter();
    await router.push("/lines/airports/subscriptions/100");
    const wrapper = mountWithRouter(router);
    await flushPromises();

    const active = wrapper.findAll(".rail-link.router-link-active").map((el) => el.text());
    expect(active).toEqual(["机场订阅"]);
  });

  it("在用户详情页时，侧栏仍高亮「用户」（用生产路由表，防止详情路由又被拆成平级）", async () => {
    const router = adminRouter();
    await router.push("/users/7");
    const wrapper = mountWithRouter(router);
    await flushPromises();

    const active = wrapper.findAll(".rail-link.router-link-active").map((el) => el.text());
    expect(active).toEqual(["用户"]);
  });
});
