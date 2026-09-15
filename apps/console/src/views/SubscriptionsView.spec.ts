import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it } from "vitest";
import type { MeSubscription } from "../api/types";
import { useAuthStore } from "../stores/auth";
import SubscriptionsView from "./SubscriptionsView.vue";

function sub(overrides: Partial<MeSubscription> = {}): MeSubscription {
  return {
    id: 1,
    assignmentNo: "7K3M9QX2FT",
    name: "Claude 月付",
    agentType: "CLAUDE",
    startsAt: "2026-08-01T00:00:00Z",
    endsAt: "2026-08-31T00:00:00Z",
    active: true,
    ...overrides,
  };
}

const stubs = { RouterLink: { template: "<a><slot /></a>" } };

function mountWith(subscriptions: MeSubscription[]) {
  const store = useAuthStore();
  store.me = { id: 1, email: "m@b.c", role: "MEMBER", subscriptions };
  return mount(SubscriptionsView, { global: { stubs } });
}

describe("SubscriptionsView", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("待开通的订阅：琥珀徽标 + 说明管理员开通后才会显示起止", async () => {
    const wrapper = mountWith([sub({ startsAt: null, endsAt: null, active: false })]);
    await flushPromises();
    const card = wrapper.get(".sub-card");
    expect(card.get(".pill.pending").text()).toBe("待开通");
    expect(card.text()).toContain("管理员开通后这里会显示起止时间");
    expect(card.get(".sub-assignment").text()).toBe("7K3M9-QX2FT");
  });

  it("在期订阅显示起止时间与绿色「在期」；已过期灰色", async () => {
    const wrapper = mountWith([sub(), sub({ id: 2, active: false, assignmentNo: "AAAAABBBBB" })]);
    await flushPromises();
    const cards = wrapper.findAll(".sub-card");
    expect(cards[0].get(".pill").text()).toBe("在期");
    expect(cards[0].text()).toContain("2026-08");
    expect(cards[1].get(".pill.muted").text()).toBe("已过期");
  });

  it("没有订阅时给出去买套餐的入口", async () => {
    const wrapper = mountWith([]);
    await flushPromises();
    expect(wrapper.text()).toContain("还没有订阅");
    expect(wrapper.find("a").text()).toBe("去购买套餐");
  });
});
