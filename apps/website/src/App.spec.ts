import { flushPromises, mount } from "@vue/test-utils";
import { createHead } from "@unhead/vue/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.vue";
import { rememberLocale } from "./i18n";
import { SITE } from "./routes";
import { installMemoryStorage, makeRouter, readHead } from "./testing";

// App.vue 自己就调 provideI18n()，不能再套 mountWithI18n 的壳（会 provide 两次），故单独挂。
// 页头页脚是真的渲染出来的——它们取文案的链路也一并测到了。
async function mountApp(path: string) {
  const router = makeRouter();
  const head = createHead();
  await router.push(path);
  await router.isReady();

  const wrapper = mount(App, { global: { plugins: [router, head] } });
  await flushPromises();
  return { wrapper, router, head };
}

beforeEach(() => {
  installMemoryStorage();
});

describe("App 的语言与 canonical", () => {
  it("html lang 随语言走", async () => {
    expect((await readHead((await mountApp("/")).head)).htmlLang).toBe("zh-CN");
    expect((await readHead((await mountApp("/en")).head)).htmlLang).toBe("en");
  });

  it("canonical 指向当前语言版本，且恒带尾斜杠", async () => {
    const { head } = await mountApp("/en");
    const { links } = await readHead(head);

    const canonical = links.filter((l) => l.rel === "canonical");
    expect(canonical).toHaveLength(1);
    expect(canonical[0].href).toBe(`${SITE}/en/`);
  });

  it("指南页的 canonical 带完整路径", async () => {
    const { head } = await mountApp("/guides/claude-code-in-china");
    const { links } = await readHead(head);

    expect(links.find((l) => l.rel === "canonical")?.href).toBe(
      `${SITE}/guides/claude-code-in-china/`,
    );
  });

  it("hreflang 三连：中英各一条 + x-default 兜底给中文", async () => {
    const { head } = await mountApp("/en/guides/claude-code-in-china");
    const { links } = await readHead(head);

    const alt = links.filter((l) => l.rel === "alternate");
    expect(alt).toHaveLength(3);
    expect(alt.find((l) => l.hreflang === "zh-CN")?.href).toBe(
      `${SITE}/guides/claude-code-in-china/`,
    );
    expect(alt.find((l) => l.hreflang === "en")?.href).toBe(
      `${SITE}/en/guides/claude-code-in-china/`,
    );
    // x-default 指中文版：未匹配到语言的用户落中文站
    expect(alt.find((l) => l.hreflang === "x-default")?.href).toBe(
      `${SITE}/guides/claude-code-in-china/`,
    );
  });

  it("两种语言下的 hreflang 指向同一对 URL，只是自己这条换了", async () => {
    const zh = await readHead((await mountApp("/")).head);
    const en = await readHead((await mountApp("/en")).head);

    const hrefs = (h: typeof zh) =>
      h.links
        .filter((l) => l.rel === "alternate")
        .map((l) => `${l.hreflang}:${l.href}`)
        .sort();
    expect(hrefs(zh)).toEqual(hrefs(en));
  });
});

describe("App 的社交卡片", () => {
  it("og:url 与 canonical 同一个地址", async () => {
    const { head } = await mountApp("/en");
    const { links, meta } = await readHead(head);

    expect(meta["og:url"]).toBe(links.find((l) => l.rel === "canonical")?.href);
  });

  it("og:locale 与备选语言成对出现，让社交平台知道另一版存在", async () => {
    const zh = await readHead((await mountApp("/")).head);
    expect(zh.meta["og:locale"]).toBe("zh_CN");
    expect(zh.meta["og:locale:alternate"]).toBe("en_US");

    const en = await readHead((await mountApp("/en")).head);
    expect(en.meta["og:locale"]).toBe("en_US");
    expect(en.meta["og:locale:alternate"]).toBe("zh_CN");
  });

  it("卡片图按语言分两张：图是二进制，「英文页不该有中文」那条验收扫不到它", async () => {
    const zh = await readHead((await mountApp("/")).head);
    expect(zh.meta["og:image"]).toBe(`${SITE}/og.png`);
    expect(zh.meta["twitter:image"]).toBe(`${SITE}/og.png`);

    const en = await readHead((await mountApp("/en")).head);
    expect(en.meta["og:image"]).toBe(`${SITE}/og-en.png`);
    expect(en.meta["twitter:image"]).toBe(`${SITE}/og-en.png`);
  });

  it("带上卡片图尺寸，社交平台才按大图渲染", async () => {
    const { meta } = await readHead((await mountApp("/")).head);

    expect(meta["og:image:width"]).toBe("1200");
    expect(meta["og:image:height"]).toBe("630");
  });
});

describe("App 的回访语言偏好", () => {
  it("上次选过英文、这次落在默认中文首页，就送去 /en/", async () => {
    rememberLocale("en");
    const { router } = await mountApp("/");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/en/");
  });

  it("没存过偏好就留在 URL 指定的语言，不做自动探测", async () => {
    const { router } = await mountApp("/");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/");
  });

  it("存的是中文时不动，只做单向的 / → /en/", async () => {
    rememberLocale("zh");
    const { router } = await mountApp("/");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/");
  });

  it("显式访问指南页时永远尊重 URL，不被偏好劫走", async () => {
    rememberLocale("en");
    const { router } = await mountApp("/guides/claude-code-in-china");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/guides/claude-code-in-china");
  });

  it("已经在 /en/ 上就不再跳，免得自己跳自己", async () => {
    rememberLocale("en");
    const { router } = await mountApp("/en");
    await flushPromises();

    expect(router.currentRoute.value.path).toBe("/en");
  });

  it("localStorage 整个不可用（隐私模式）时只是记不住，页面照常渲染", async () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new DOMException("denied", "SecurityError");
      },
      setItem: () => {
        throw new DOMException("denied", "SecurityError");
      },
    });

    const { router, wrapper } = await mountApp("/");
    expect(router.currentRoute.value.path).toBe("/");
    expect(wrapper.find("header").exists()).toBe(true);
  });
});

describe("App 的骨架", () => {
  it("页头、路由出口、页脚三段齐全", async () => {
    const { wrapper } = await mountApp("/");

    expect(wrapper.find("header.header").exists()).toBe(true);
    expect(wrapper.find("footer.footer").exists()).toBe(true);
  });
});
