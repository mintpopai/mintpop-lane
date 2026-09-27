import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BizError } from "../api/http";
import SubImportModal from "./SubImportModal.vue";

const createNodeGroup = vi.fn(async () => 1);
const importNodeGroup = vi.fn(async () => undefined);
const showToast = vi.fn();

vi.mock("../api", () => ({
  adminApi: () => ({ createNodeGroup, importNodeGroup }),
}));
vi.mock("../toast", () => ({ showToast: (...args: unknown[]) => showToast(...args) }));

beforeEach(() => {
  vi.clearAllMocks();
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

/** 底部提交按钮是 footer 里的最后一个 admin-btn */
function submitButton(): DOMWrapper<Element> {
  const buttons = Array.from(document.querySelectorAll("button.admin-btn"));
  const last = buttons.at(-1);
  if (!last) {
    throw new Error("未找到提交按钮");
  }
  return new DOMWrapper(last);
}

function mountModal(group: null | { id: number; name: string }) {
  return mount(SubImportModal, { attachTo: document.body, props: { group: group as never } });
}

describe("SubImportModal", () => {
  it("创建模式：链接、分组名、备注一屏填完，没有节点勾选列表", async () => {
    mountModal(null);

    expect(document.querySelector("#sub-url")).not.toBeNull();
    expect(document.querySelector("#group-name")).not.toBeNull();
    expect(document.querySelector("#group-remark")).not.toBeNull();
    expect(document.querySelector("table")).toBeNull();
    expect(document.body.textContent).toContain("自动导入订阅里的美国节点");
  });

  it("创建模式：填链接与分组名后一次提交，只发名字、链接、备注", async () => {
    const wrapper = mountModal(null);
    await query("#sub-url").setValue("  https://sub.example.com/c?token=t  ");
    await query("#group-name").setValue(" 机场A ");
    await submitButton().trigger("click");

    await vi.waitFor(() =>
      expect(createNodeGroup).toHaveBeenCalledWith({
        name: "机场A",
        subUrl: "https://sub.example.com/c?token=t",
        remark: "",
      }),
    );
    expect(showToast).toHaveBeenCalledWith("success", "已创建分组并导入美国节点");
    expect(wrapper.emitted("saved")).toBeTruthy();
    expect(wrapper.emitted("close")).toBeTruthy();
  });

  it("缺链接或缺分组名时不发请求，直接提示", async () => {
    mountModal(null);
    await submitButton().trigger("click");
    expect(showToast).toHaveBeenLastCalledWith("error", "先粘贴订阅链接");

    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
    await submitButton().trigger("click");
    expect(showToast).toHaveBeenLastCalledWith("error", "给这个分组起个名字");

    expect(createNodeGroup).not.toHaveBeenCalled();
  });

  it("订阅里没有美国节点时把服务端的说法原样提示，弹窗不关", async () => {
    createNodeGroup.mockRejectedValueOnce(
      new BizError(410049, "订阅里没有美国节点（节点名带 🇺🇸 或 [US]），未导入"),
    );
    const wrapper = mountModal(null);
    await query("#sub-url").setValue("https://sub.example.com/c?token=t");
    await query("#group-name").setValue("机场A");
    await submitButton().trigger("click");

    await vi.waitFor(() =>
      expect(showToast).toHaveBeenCalledWith(
        "error",
        "订阅里没有美国节点（节点名带 🇺🇸 或 [US]），未导入",
      ),
    );
    expect(wrapper.emitted("close")).toBeFalsy();
  });

  it("重新拉取模式：不出现链接与分组名输入框，一键调用 importNodeGroup", async () => {
    const wrapper = mountModal({ id: 7, name: "机场A" });

    expect(document.querySelector("#sub-url")).toBeNull();
    expect(document.querySelector("#group-name")).toBeNull();
    await submitButton().trigger("click");

    await vi.waitFor(() => expect(importNodeGroup).toHaveBeenCalledWith(7));
    expect(showToast).toHaveBeenCalledWith("success", "已重新拉取并导入美国节点");
    expect(wrapper.emitted("saved")).toBeTruthy();
  });
});
