// 中英文案的结构比对：interface 只能保证 zh / en 两侧字段齐全，保证不了数组条数一致——
// 比如中文 faq.items 写 5 条、英文写 4 条，照样编译通过、照样上线。
// 这里递归比对结构（数组长度、对象 key 集合），不比较文案内容本身（中英文案本就该不同）。
// copy.test.ts 与 guides.test.ts 共用。

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** 递归比对结构，把发现的每处不一致连同路径（如 COPY.faq.items）记进 errors，方便定位 */
export function diffStructure(zh: unknown, en: unknown, path: string, errors: string[]): void {
  if (Array.isArray(zh) || Array.isArray(en)) {
    if (!Array.isArray(zh) || !Array.isArray(en)) {
      errors.push(`${path}：一侧是数组、一侧不是`);
      return;
    }
    if (zh.length !== en.length) {
      errors.push(`${path}：数组长度不一致（zh=${zh.length} 条，en=${en.length} 条）`);
      return;
    }
    zh.forEach((item, i) => diffStructure(item, en[i], `${path}[${i}]`, errors));
    return;
  }

  if (isPlainObject(zh) || isPlainObject(en)) {
    if (!isPlainObject(zh) || !isPlainObject(en)) {
      errors.push(`${path}：一侧是对象、一侧不是`);
      return;
    }
    const zhKeys = Object.keys(zh).sort();
    const enKeys = Object.keys(en).sort();
    if (zhKeys.join(",") !== enKeys.join(",")) {
      errors.push(
        `${path}：字段集合不一致（zh=[${zhKeys.join(", ")}]，en=[${enKeys.join(", ")}]）`,
      );
      return;
    }
    for (const key of zhKeys) {
      diffStructure(zh[key], en[key], `${path}.${key}`, errors);
    }
    return;
  }

  // 到这里两侧都是基本类型（string/number/...）：不比较值本身，文案本就该不同
}
