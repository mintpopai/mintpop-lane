// 指南注册表：顺序即首页「延伸阅读」与页脚的展示顺序。
// 新增一篇 = 在 guides/ 下建文件 + 这里加一行；路由、sitemap 校验、首页列表、页脚链接都从这张表派生。
import { claudeCodeInChina } from "./claude-code-in-china";
import type { Guide } from "./types";

export type { Guide, GuideCopy, GuideSection } from "./types";

export const GUIDES: Guide[] = [{ slug: "claude-code-in-china", copy: claudeCodeInChina }];

export function findGuide(slug: string): Guide | undefined {
  return GUIDES.find((g) => g.slug === slug);
}
