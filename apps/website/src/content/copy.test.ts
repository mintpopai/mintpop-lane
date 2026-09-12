// interface Copy 只能保证 zh / en 两侧字段齐全，保证不了数组条数一致——
// 比如中文 faq.items 写 5 条、英文写 4 条，照样编译通过、照样上线，
// 类型系统与产物 grep 都不一定拦得住。这条测试守的就是 TS 抓不到的这一类结构缺陷：
// 递归比对 COPY.zh 与 COPY.en 的结构（数组长度、对象 key 集合），不比较文案内容本身
// （中英文案本就该不同，值的差异不是 bug）。
import { describe, expect, it } from "vitest";
import { COPY } from "./copy";
import { diffStructure } from "./structure";

describe("COPY 中英结构一致性", () => {
  it("zh 与 en 的数组条数、对象字段集合逐层一致", () => {
    const errors: string[] = [];
    diffStructure(COPY.zh, COPY.en, "COPY", errors);
    expect(errors).toEqual([]);
  });
});

// 联系入口是跨站到 MintPop 主站的硬编码绝对地址，两条路径只差一个 /zh 段，
// 上面那条结构测试对「值」是不看的——两边写成同一个 URL 也照样通过。
// 这里把两条地址钉住：写反了会把中文访客送到英文联系页（反之亦然）。
describe("联系入口按语言分流", () => {
  it("中文站去 /zh/contact，英文站去 /contact", () => {
    expect(COPY.zh.ui.contact.href).toBe("https://mintpop.ai/zh/contact");
    expect(COPY.en.ui.contact.href).toBe("https://mintpop.ai/contact");
  });
});
