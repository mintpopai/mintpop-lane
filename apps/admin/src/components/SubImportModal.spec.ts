import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SubPreviewNode } from "../api/types";
import SubImportModal from "./SubImportModal.vue";

const previewSub = vi.fn<(body: unknown) => Promise<SubPreviewNode[]>>();
const createNodeGroup = vi.fn(async () => 1);
const refreshPreviewNodeGroup = vi.fn<(id: number) => Promise<SubPreviewNode[]>>();
const importNodeGroup = vi.fn(async () => undefined);

vi.mock("../api", () => ({
  adminApi: () => ({ previewSub, createNodeGroup, refreshPreviewNodeGroup, importNodeGroup }),
}));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

const previewNodes: SubPreviewNode[] = [
  { sourceName: "剩余流量：10 GB", sourceType: "anytls", serverAddr: "a.example.com", port: 1, suspectedInfo: true, existed: false },
  { sourceName: "香港-01", sourceType: "anytls", serverAddr: "hk.example.com", port: 2, suspectedInfo: false, existed: false },
  { sourceName: "已入池的", sourceType: "vless", serverAddr: "b.example.com", port: 3, suspectedInfo: false, existed: true },
];

beforeEach(() => {
  vi.clearAllMocks();
  previewSub.mockResolvedValue(previewNodes);
  refreshPreviewNodeGroup.mockResolvedValue(previewNodes);
});

afterEach(() => {
  // attachTo: document.body 会把弹窗真实挂到 body 上，测试间要清掉，不然下一次挂载会撞上上一次残留的 DOM
  document.body.innerHTML = "";
});

/** AdminModal 用 Teleport 挂到 body，游离节点里的内容 wrapper.find 够不着，须从 document 上取，仿 AdminModal.spec.ts 的做法 */
function query(selector: string): DOMWrapper<Element> {
  const el = document.querySelector(selector);
  if (!el) {
    throw new Error(`未找到元素：${selector}`);
  }
  return new DOMWrapper(el);
}

function queryAll(selector: string): DOMWrapper<Element>[] {
  return Array.from(document.querySelectorAll(selector)).map((el) => new DOMWrapper(el));
}

async function openPreviewList(group: null | { id: number } = null) {
  const wrapper = mount(SubImportModal, {
    attachTo: document.body,
    props: { group: group as never },
  });
  if (!group) {
    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
  }
  await query("button.admin-btn").trigger("click");
  await vi.waitFor(() => expect(document.querySelectorAll("tbody tr")).toHaveLength(3));
  return wrapper;
}

describe("SubImportModal", () => {
  it("拉取预览后展示全部条目，疑似信息条目与已入池的默认不勾", async () => {
    await openPreviewList();

    expect(previewSub).toHaveBeenCalledWith({ subUrl: "https://sub.example.com/c?token=t" });
    const checkboxes = queryAll("tbody input[type=checkbox]");
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(false);
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(true);
    expect((checkboxes[2].element as HTMLInputElement).checked).toBe(false);
    expect(document.body.textContent).toContain("已入池");
    expect(document.body.textContent).toContain("疑似信息条目");
  });

  it("创建模式：填分组名提交后按勾选调用 createNodeGroup", async () => {
    const wrapper = await openPreviewList();
    await query("#group-name").setValue("机场A");
    // 底部提交按钮是 footer 里的最后一个 admin-btn
    const submitButton = queryAll("button.admin-btn").at(-1)!;
    await submitButton.trigger("click");

    await vi.waitFor(() =>
      expect(createNodeGroup).toHaveBeenCalledWith({
        name: "机场A",
        subUrl: "https://sub.example.com/c?token=t",
        selectedNames: ["香港-01"],
        remark: "",
      }),
    );
    expect(wrapper.emitted("saved")).toBeTruthy();
  });

  it("重新拉取模式：不出现链接与分组名输入框，提交调用 importNodeGroup", async () => {
    await openPreviewList({ id: 7 });

    expect(document.querySelector("#sub-url")).toBeNull();
    expect(document.querySelector("#group-name")).toBeNull();
    expect(refreshPreviewNodeGroup).toHaveBeenCalledWith(7);

    const submitButton = queryAll("button.admin-btn").at(-1)!;
    await submitButton.trigger("click");
    await vi.waitFor(() =>
      expect(importNodeGroup).toHaveBeenCalledWith(7, { selectedNames: ["香港-01"] }),
    );
  });

  it("全选/清空切换", async () => {
    await openPreviewList();
    const checkAllInput = query(".node-check-all input[type=checkbox]");
    await checkAllInput.setValue(true);
    expect(document.body.textContent).toContain("已选 3 / 3");
    await checkAllInput.setValue(false);
    expect(document.body.textContent).toContain("已选 0 / 3");
  });
});
