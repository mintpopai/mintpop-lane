// 指南注册表的三条结构约束：中英结构一致（类型抓不到数组条数）、slug 合法且唯一、日期是 ISO 格式。
import { describe, expect, it } from "vitest";
import { diffStructure } from "../structure";
import { GUIDES, findGuide } from "./index";

describe("GUIDES 注册表", () => {
  it("至少有一篇", () => {
    expect(GUIDES.length).toBeGreaterThan(0);
  });

  it("每篇 zh 与 en 的结构逐层一致", () => {
    const errors: string[] = [];
    for (const g of GUIDES) diffStructure(g.copy.zh, g.copy.en, `GUIDES[${g.slug}]`, errors);
    expect(errors).toEqual([]);
  });

  it("slug 只含小写字母、数字、连字符，且全表唯一", () => {
    const slugs = GUIDES.map((g) => g.slug);
    for (const s of slugs) expect(s).toMatch(/^[a-z0-9]+(-[a-z0-9]+)*$/);
    expect(new Set(slugs).size).toBe(slugs.length);
  });

  it("published / updated 是 YYYY-MM-DD 且 updated 不早于 published", () => {
    for (const g of GUIDES) {
      for (const c of [g.copy.zh, g.copy.en]) {
        expect(c.published).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        expect(c.updated).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        expect(c.updated >= c.published).toBe(true);
      }
    }
  });

  it("findGuide 按 slug 查找，找不到给 undefined", () => {
    expect(findGuide(GUIDES[0].slug)).toBe(GUIDES[0]);
    expect(findGuide("nope")).toBeUndefined();
  });
});
