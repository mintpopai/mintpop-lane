// 官网组件测试的公共夹具。
//
// 每个组件都经 useI18n() 取文案，而 locale 是 App.vue 用 provideI18n() 注入的
// （刻意不做模块单例，理由见 i18n.ts），provideI18n 内部又要读 useRoute()。
// 于是哪怕只挂一个纯展示的 section，也得先备齐「真路由 + 一层 provide 壳 + head 实例」。
// 这里备一次，各 spec 直接用，免得每个文件重抄一遍样板。

import { mount } from "@vue/test-utils";
import { vi } from "vitest";
import { createHead } from "@unhead/vue/server";
import type { VueHeadClient } from "@unhead/vue";
import { defineComponent, h, type Component } from "vue";
import { createMemoryHistory, createRouter, type Router } from "vue-router";
import { localePath, provideI18n, type Locale } from "./i18n";

/** 占位组件：路由表只为把 URL 解析成 route.path / route.params，不需要真渲染页面 */
const BLANK = defineComponent({ name: "BlankRoute", render: () => h("div") });

/**
 * 路由表与 main.ts 逐条对齐——尤其是两条带 `:slug` 的动态路由：
 * 若在这里改用 routes.ts 里展开好的具体路径（/guides/claude-code-in-china），
 * 匹配到的就是静态路由、route.params.slug 恒为空，GuidePage 会一路测成「找不到指南」。
 */
export function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: BLANK },
      { path: "/en", component: BLANK },
      { path: "/guides/:slug", component: BLANK },
      { path: "/en/guides/:slug", component: BLANK },
    ],
  });
}

export interface MountI18nOptions {
  /** 停在哪个 URL：同时决定语言与 route.params。不传则按 locale 取对应首页 */
  path?: string;
  /** 只想换语言、不关心具体路径时用（zh → /，en → /en/） */
  locale?: Locale;
  props?: Record<string, unknown>;
}

/**
 * 在「App.vue 之下」的等价环境里挂载一个组件。
 *
 * 返回的 wrapper 是外层壳的 wrapper——DOM 断言（find/text/html）照常用；
 * 要拿被测组件自身的实例或 emitted，用 wrapper.findComponent(组件)。
 */
export async function mountWithI18n(component: Component, options: MountI18nOptions = {}) {
  const { path, locale = "zh", props } = options;
  const router = makeRouter();
  const head = createHead();

  await router.push(path ?? localePath(locale));
  await router.isReady();

  // 壳只做一件事：在被测组件之上调 provideI18n()，复刻 App.vue 的注入链路
  const Host = defineComponent({
    name: "I18nHost",
    setup() {
      provideI18n();
      return () => h(component, props);
    },
  });

  const wrapper = mount(Host, { global: { plugins: [router, head] } });
  return { wrapper, router, head };
}

/** useHead 的产物，归一化成好断言的形状（title / lang / meta / link / JSON-LD） */
export interface HeadSnapshot {
  title: string | undefined;
  htmlLang: string | undefined;
  /** name 或 property → content，两种 key 合在一张表里 */
  meta: Record<string, string>;
  links: { rel: string; href: string; hreflang?: string }[];
  /** 逐段 application/ld+json 解析出的对象，顺序与声明顺序一致 */
  jsonLd: Record<string, unknown>[];
}

export async function readHead(head: VueHeadClient): Promise<HeadSnapshot> {
  const tags = await head.resolveTags();
  const meta: Record<string, string> = {};
  const links: HeadSnapshot["links"] = [];
  const jsonLd: Record<string, unknown>[] = [];
  let title: string | undefined;
  let htmlLang: string | undefined;

  for (const tag of tags) {
    const props = tag.props as Record<string, string | undefined>;
    if (tag.tag === "title") title = String(tag.textContent ?? "");
    else if (tag.tag === "htmlAttrs") htmlLang = props.lang;
    else if (tag.tag === "meta") {
      const key = props.name ?? props.property;
      if (key && props.content !== undefined) meta[key] = props.content;
    } else if (tag.tag === "link" && props.rel && props.href) {
      links.push({ rel: props.rel, href: props.href, hreflang: props.hreflang });
    } else if (tag.tag === "script" && props.type === "application/ld+json") {
      jsonLd.push(JSON.parse(String(tag.innerHTML)) as Record<string, unknown>);
    }
  }

  return { title, htmlLang, meta, links, jsonLd };
}

/**
 * 换一份内存版 localStorage，并返回它。
 *
 * 两个理由都得治：① jsdom 30 跑在 node 26 上时 window.localStorage 压根是 undefined
 * （node 自己那份要 --localstorage-file 才开，jsdom 没有补上），直接用会 TypeError；
 * ② 就算有，同一进程的用例之间也是共享的，上一个 it 存的值会漏给下一个。
 * 凡是碰语言偏好（i18n 的 rememberLocale / savedLocale）的 spec，都在 beforeEach 里调一次。
 */
export function installMemoryStorage(): Storage {
  const store = new Map<string, string>();
  const storage: Storage = {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => void store.set(k, String(v)),
    removeItem: (k) => void store.delete(k),
    clear: () => store.clear(),
    key: (i) => [...store.keys()][i] ?? null,
    get length() {
      return store.size;
    },
  };
  vi.stubGlobal("localStorage", storage);
  return storage;
}
