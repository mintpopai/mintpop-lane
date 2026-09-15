// diffStructure 是 copy.spec.ts 与 guides.spec.ts 的判据本身：
// 它要是漏报，那两个文件就会在中英文案已经对不上的情况下静默变绿。故单独把它钉死。
import { describe, expect, it } from "vitest";
import { diffStructure } from "./structure";

/** 跑一遍比对，把记下的问题原样返回 */
function diff(zh: unknown, en: unknown): string[] {
  const errors: string[] = [];
  diffStructure(zh, en, "COPY", errors);
  return errors;
}

describe("diffStructure 放行的情况", () => {
  it("结构相同、文案不同：正是中英两版该有的样子", () => {
    expect(
      diff({ title: "标题", items: ["一", "二"] }, { title: "Title", items: ["a", "b"] }),
    ).toEqual([]);
  });

  it("不比较基本类型的值本身，类型不同也放行（文案本就该不同）", () => {
    expect(diff({ n: 1 }, { n: 2 })).toEqual([]);
    expect(diff({ v: "1" }, { v: 2 })).toEqual([]);
  });

  it("嵌套对象与数组逐层对下去，深处一致就放行", () => {
    const zh = { faq: { items: [{ q: "问", a: "答" }] } };
    const en = { faq: { items: [{ q: "Q", a: "A" }] } };
    expect(diff(zh, en)).toEqual([]);
  });

  it("字段顺序不同不算不一致，只看 key 的集合", () => {
    expect(diff({ a: "1", b: "2" }, { b: "x", a: "y" })).toEqual([]);
  });

  it("两侧都是空数组、空对象时放行", () => {
    expect(diff({ list: [], obj: {} }, { list: [], obj: {} })).toEqual([]);
  });
});

describe("diffStructure 该报的情况", () => {
  it("数组条数不一致时报出来，并说清各是几条——这正是 interface 管不住的那类漏翻", () => {
    const errors = diff({ items: ["一", "二", "三"] }, { items: ["a", "b"] });

    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain("COPY.items");
    expect(errors[0]).toContain("zh=3");
    expect(errors[0]).toContain("en=2");
  });

  it("字段集合不一致时把两边的 key 都列出来，方便定位", () => {
    const errors = diff({ a: "1", b: "2" }, { a: "x", c: "y" });

    expect(errors).toHaveLength(1);
    expect(errors[0]).toContain("COPY：字段集合不一致");
    expect(errors[0]).toContain("a, b");
    expect(errors[0]).toContain("a, c");
  });

  it("一侧是数组、另一侧不是", () => {
    const errors = diff({ x: ["一"] }, { x: "a" });

    expect(errors).toEqual(["COPY.x：一侧是数组、一侧不是"]);
  });

  it("一侧是对象、另一侧不是", () => {
    const errors = diff({ x: { k: "一" } }, { x: "a" });

    expect(errors).toEqual(["COPY.x：一侧是对象、一侧不是"]);
  });

  it("路径一路带着下标与字段名，直接指到出问题那一处", () => {
    const zh = {
      faq: {
        items: [
          { q: "问", a: "答" },
          { q: "问2", tags: ["x"] },
        ],
      },
    };
    const en = {
      faq: {
        items: [
          { q: "Q", a: "A" },
          { q: "Q2", tags: [] },
        ],
      },
    };

    expect(diff(zh, en)).toEqual(["COPY.faq.items[1].tags：数组长度不一致（zh=1 条，en=0 条）"]);
  });

  it("同一层的多处不一致会一次全报出来，不是报了第一处就收工", () => {
    const zh = { a: ["一", "二"], b: ["三"] };
    const en = { a: ["x"], b: [] };

    expect(diff(zh, en)).toHaveLength(2);
  });

  it("数组条数已经对不上时不再往下钻，免得刷出一串派生错误", () => {
    const zh = { items: [{ q: "问" }, { q: "问2" }] };
    const en = { items: [{ different: "x" }] };

    // 只报长度这一条，不再为 items[0] 的字段集合各报一条
    expect(diff(zh, en)).toHaveLength(1);
  });

  it("null 不当对象看：两侧都是 null 放行，一侧 null 一侧对象要报", () => {
    expect(diff({ x: null }, { x: null })).toEqual([]);
    expect(diff({ x: null }, { x: { k: "v" } })).toEqual(["COPY.x：一侧是对象、一侧不是"]);
  });
});
