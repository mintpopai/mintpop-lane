// @vitest-environment node
//
// 本文件要按 import.meta.url 去磁盘上读 sitemap.xml，而 jsdom 环境下 import.meta.url 是 http 协议、
// readFileSync 收不下；这一个 spec 单独钉回 node 环境（默认 jsdom 见 vite.config.ts）。

// sitemap 是手写的，路由是从注册表生成的——两者一旦漂移（新指南忘加 sitemap、或 sitemap 里残留旧 URL），
// 搜索引擎要么收录不到、要么抓到 404。这里把两边的 URL 集合钉成完全相等。
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { GUIDES } from "./content/guides";
import { canonicalPath, guidePath, ROUTE_PATHS, SITE } from "./routes";

describe("guidePath / canonicalPath", () => {
  it("指南路径按语言加前缀，恒带尾斜杠", () => {
    expect(guidePath("zh", "x")).toBe("/guides/x/");
    expect(guidePath("en", "x")).toBe("/en/guides/x/");
  });

  it("canonicalPath 补尾斜杠，根路径保持 /", () => {
    expect(canonicalPath("/")).toBe("/");
    expect(canonicalPath("/en")).toBe("/en/");
    expect(canonicalPath("/en/")).toBe("/en/");
    expect(canonicalPath("/guides/x")).toBe("/guides/x/");
  });
});

describe("ROUTE_PATHS", () => {
  it("首页两种语言 + 每篇指南两种语言", () => {
    expect(ROUTE_PATHS).toContain("/");
    expect(ROUTE_PATHS).toContain("/en");
    for (const g of GUIDES) {
      expect(ROUTE_PATHS).toContain(`/guides/${g.slug}`);
      expect(ROUTE_PATHS).toContain(`/en/guides/${g.slug}`);
    }
    expect(ROUTE_PATHS.length).toBe(2 + GUIDES.length * 2);
  });

  it("sitemap.xml 的 <loc> 集合与预渲染路径的 canonical URL 集合完全相等", () => {
    const xml = readFileSync(new URL("../public/sitemap.xml", import.meta.url), "utf8");
    const locs = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]).sort();
    const expected = ROUTE_PATHS.map((p) => SITE + canonicalPath(p)).sort();
    expect(locs).toEqual(expected);
  });
});
