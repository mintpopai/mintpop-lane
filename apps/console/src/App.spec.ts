import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.vue";
import { showToast, toast } from "./toast";

function render() {
  return mount(App, { global: { stubs: { RouterView: { template: "<div class='page' />" } } } });
}

beforeEach(() => {
  vi.useFakeTimers();
  toast.value = null;
});

afterEach(() => {
  vi.useRealTimers();
});

describe("App", () => {
  it("顶层就是一个路由出口，gate 页与后台两种形态都从这里出", () => {
    expect(render().find(".page").exists()).toBe(true);
  });

  it("没有提示时不占位，页面上看不到空盒子", () => {
    expect(render().find(".toast").exists()).toBe(false);
  });

  it("提示出现时带 role=status，读屏不用抢焦点也能念到", async () => {
    const wrapper = render();

    showToast("success", "已保存");
    await wrapper.vm.$nextTick();

    const el = wrapper.get(".toast");
    expect(el.attributes("role")).toBe("status");
    expect(el.text()).toBe("已保存");
  });

  it("成功与失败各配一个类，不是只靠文案区分", async () => {
    const wrapper = render();

    showToast("success", "成了");
    await wrapper.vm.$nextTick();
    expect(wrapper.get(".toast").classes()).toContain("success");

    showToast("error", "崩了");
    await wrapper.vm.$nextTick();
    expect(wrapper.get(".toast").classes()).toContain("error");
    expect(wrapper.get(".toast").classes()).not.toContain("success");
  });

  it("到点自动消失", async () => {
    const wrapper = render();
    showToast("error", "崩了");
    await wrapper.vm.$nextTick();
    expect(wrapper.find(".toast").exists()).toBe(true);

    vi.advanceTimersByTime(3000);
    await wrapper.vm.$nextTick();
    expect(wrapper.find(".toast").exists()).toBe(false);
  });
});
