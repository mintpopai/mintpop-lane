import { beforeEach, describe, expect, it, vi } from "vitest";
import { localeFromPath, localePath, rememberLocale, savedLocale, switchLocalePath } from "./i18n";

// node 26 默认不提供 localStorage（要 --localstorage-file 才开），这里给一个最小内存实现。
// 仍用 node 环境而不是 jsdom：本文件测的都是纯逻辑，为一个 getItem 引整套 DOM 不划算。
function memoryStorage(): Storage {
  const store = new Map<string, string>();
  return {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, v),
    removeItem: (k: string) => void store.delete(k),
    clear: () => store.clear(),
    key: (i: number) => [...store.keys()][i] ?? null,
    get length() {
      return store.size;
    },
  };
}

beforeEach(() => {
  vi.stubGlobal("localStorage", memoryStorage());
});

describe("localeFromPath", () => {
  it("语言的单一来源是 URL：/en 与 /en/** 是英文", () => {
    expect(localeFromPath("/en")).toBe("en");
    expect(localeFromPath("/en/")).toBe("en");
    expect(localeFromPath("/en/anything")).toBe("en");
  });

  it("其余路径一律中文，默认语言在根路径", () => {
    expect(localeFromPath("/")).toBe("zh");
    expect(localeFromPath("/download")).toBe("zh");
  });

  it("不把前缀相同的别的路径误判成英文", () => {
    expect(localeFromPath("/enterprise")).toBe("zh");
  });
});

describe("localePath", () => {
  it("中文在根，英文带尾斜杠（与预渲染出的目录形态一致）", () => {
    expect(localePath("zh")).toBe("/");
    expect(localePath("en")).toBe("/en/");
  });
});

describe("switchLocalePath", () => {
  it("首页互切：/ ↔ /en/，不带尾斜杠的 /en 也认", () => {
    expect(switchLocalePath("/", "en")).toBe("/en/");
    expect(switchLocalePath("/en/", "zh")).toBe("/");
    expect(switchLocalePath("/en", "zh")).toBe("/");
  });

  it("指南页互切：保留 slug，只换语言前缀", () => {
    expect(switchLocalePath("/guides/x/", "en")).toBe("/en/guides/x/");
    expect(switchLocalePath("/en/guides/x/", "zh")).toBe("/guides/x/");
  });

  it("切到当前语言时原样返回，但补齐尾斜杠（预渲染期 route.path 不带斜杠）", () => {
    expect(switchLocalePath("/guides/x", "zh")).toBe("/guides/x/");
    expect(switchLocalePath("/en/guides/x", "en")).toBe("/en/guides/x/");
  });

  it("不把 /enterprise 这类前缀相同的路径当英文", () => {
    expect(switchLocalePath("/enterprise/", "en")).toBe("/en/enterprise/");
  });
});

describe("savedLocale", () => {
  it("没存过时给 null，交给调用方走默认语言", () => {
    expect(savedLocale()).toBeNull();
  });

  it("存过什么就读回什么", () => {
    rememberLocale("en");
    expect(savedLocale()).toBe("en");
    rememberLocale("zh");
    expect(savedLocale()).toBe("zh");
  });

  it("存着不认识的值时给 null，不让脏数据漏进 locale", () => {
    localStorage.setItem("lane-locale", "ja");
    expect(savedLocale()).toBeNull();
  });
});
