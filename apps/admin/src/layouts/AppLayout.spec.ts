import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { defineComponent } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
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

  it("在订阅详情页时，侧栏仍高亮「机场订阅」（真路由，验证嵌套路由的激活规则）", async () => {
    const Blank = defineComponent({ template: "<div />" });
    // 与 router/index.ts 同构的最小路由表：详情是机场订阅的子路由，列表是空路径子路由
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/users", name: "USERS", component: Blank },
        { path: "/device-requests", name: "DEVICE_REQUESTS", component: Blank },
        {
          path: "/lines/airports",
          children: [
            { path: "", name: "AIRPORT_SUBSCRIPTIONS", component: Blank },
            { path: "subscriptions/:id", name: "SUBSCRIPTION_DETAIL", component: Blank },
          ],
        },
        { path: "/lines/land-nodes", name: "LAND_NODES", component: Blank },
        { path: "/plans", name: "PLANS", component: Blank },
        { path: "/enterprises", name: "ENTERPRISES", component: Blank },
        { path: "/link-health", name: "LINK_HEALTH", component: Blank },
        { path: "/settings", name: "SETTINGS", component: Blank },
      ],
    });
    await router.push("/lines/airports/subscriptions/100");
    const wrapper = mount(AppLayout, { global: { plugins: [router] } });
    await flushPromises();

    const active = wrapper.findAll(".rail-link.router-link-active").map((el) => el.text());
    expect(active).toEqual(["机场订阅"]);
  });
});
