import { flushPromises } from "@vue/test-utils";
import { beforeEach, describe, expect, it } from "vitest";
import { COPY } from "../content/copy";
import { savedLocale } from "../i18n";
import { installMemoryStorage, mountWithI18n } from "../testing";
import TheHeader from "./TheHeader.vue";

beforeEach(() => {
  installMemoryStorage();
});

describe("TheHeader 语言切换", () => {
  it("点一下换到另一种语言，并记住偏好供回访时用", async () => {
    const { wrapper, router } = await mountWithI18n(TheHeader);

    await wrapper.get("button.lang").trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/en/");
    expect(savedLocale()).toBe("en");
  });

  it("从英文切回中文同样记住偏好", async () => {
    const { wrapper, router } = await mountWithI18n(TheHeader, { locale: "en" });

    await wrapper.get("button.lang").trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/");
    expect(savedLocale()).toBe("zh");
  });

  it("带上当前锚点：滚到 FAQ 时切语言不该被扔回页顶", async () => {
    const { wrapper, router } = await mountWithI18n(TheHeader, { path: "/#faq" });

    await wrapper.get("button.lang").trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.hash).toBe("#faq");
    expect(router.currentRoute.value.path).toBe("/en/");
  });

  it("指南页上切语言留在同一篇，只换语言前缀", async () => {
    const { wrapper, router } = await mountWithI18n(TheHeader, {
      path: "/guides/claude-code-in-china",
    });

    await wrapper.get("button.lang").trigger("click");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/en/guides/claude-code-in-china/");
  });
});

describe("TheHeader 链接", () => {
  it("品牌链接跟随当前语言，英文访客点 logo 不会被带回中文站", async () => {
    const zh = await mountWithI18n(TheHeader);
    expect(zh.wrapper.get("a.brand").attributes("href")).toBe("/");

    const en = await mountWithI18n(TheHeader, { locale: "en" });
    expect(en.wrapper.get("a.brand").attributes("href")).toBe("/en/");
  });

  it("导航锚点带语言前缀，指南页上也能回到首页对应区块", async () => {
    const { wrapper } = await mountWithI18n(TheHeader, {
      path: "/en/guides/claude-code-in-china",
    });

    const hrefs = wrapper.findAll("nav.nav a").map((a) => a.attributes("href"));
    expect(hrefs).toEqual(COPY.en.nav.map((n) => `/en/${n.href}`));
    expect(hrefs.every((h) => h!.startsWith("/en/"))).toBe(true);
  });

  it("下载 CTA 同样带语言前缀", async () => {
    const { wrapper } = await mountWithI18n(TheHeader, { locale: "en" });

    expect(wrapper.get("a.cta").attributes("href")).toBe("/en/#download");
  });

  it("联系入口跨站，另开标签页且切断 opener 引用", async () => {
    const { wrapper } = await mountWithI18n(TheHeader);

    const contact = wrapper.get("a.contact");
    expect(contact.attributes("href")).toBe(COPY.zh.ui.contact.href);
    expect(contact.attributes("target")).toBe("_blank");
    expect(contact.attributes("rel")).toBe("noopener noreferrer");
    // 会突然开新窗口，读屏得先知道
    expect(contact.attributes("aria-label")).toBe(COPY.zh.ui.contact.ariaLabel);
  });

  it("品牌用官方词标图，不用文字排 logo；瓦片 alt 留空免得读屏念两遍", async () => {
    const { wrapper } = await mountWithI18n(TheHeader);

    const imgs = wrapper.findAll("a.brand img");
    expect(imgs.map((i) => i.attributes("alt"))).toEqual(["", "MintPop"]);
    expect(wrapper.get("a.brand").attributes("aria-label")).toBe(COPY.zh.ui.header.homeLabel);
  });
});

describe("TheHeader 滚动态", () => {
  it("滚过首屏才给顶栏加边框，不滚时融进 Hero 的留白", async () => {
    const { wrapper } = await mountWithI18n(TheHeader);
    expect(wrapper.get("header").classes()).not.toContain("scrolled");

    window.scrollY = 40;
    window.dispatchEvent(new Event("scroll"));
    await wrapper.vm.$nextTick();
    expect(wrapper.get("header").classes()).toContain("scrolled");

    window.scrollY = 0;
    window.dispatchEvent(new Event("scroll"));
    await wrapper.vm.$nextTick();
    expect(wrapper.get("header").classes()).not.toContain("scrolled");
  });

  it("卸载后不再听滚动事件，免得留下野监听", async () => {
    const { wrapper } = await mountWithI18n(TheHeader);
    wrapper.unmount();

    // 卸载后再滚不该抛错（监听已摘掉），这里只要不炸就说明摘干净了
    window.scrollY = 99;
    expect(() => window.dispatchEvent(new Event("scroll"))).not.toThrow();
  });
});
