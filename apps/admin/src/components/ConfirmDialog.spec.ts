import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ConfirmDialog from "./ConfirmDialog.vue";

function render(props: Partial<InstanceType<typeof ConfirmDialog>["$props"]> = {}) {
  return mount(ConfirmDialog, {
    props: { title: "删除节点", message: "删除后不可恢复。", ...props },
    // Teleport 到 body，挂进一个真实容器才查得到
    attachTo: document.body,
  });
}

describe("ConfirmDialog", () => {
  it("标题与说明都摆出来，让人知道自己要删的是什么", () => {
    const wrapper = render();

    expect(document.body.querySelector(".message")?.textContent).toBe("删除后不可恢复。");
    expect(document.body.querySelector(".dialog")?.getAttribute("aria-label")).toBe("删除节点");
    wrapper.unmount();
  });

  it("确认按钮默认写「删除」，可按场景改字", () => {
    const fallback = render();
    expect(document.body.querySelector(".admin-btn.danger")?.textContent?.trim()).toBe("删除");
    fallback.unmount();

    const custom = render({ confirmText: "吊销" });
    expect(document.body.querySelector(".admin-btn.danger")?.textContent?.trim()).toBe("吊销");
    custom.unmount();
  });

  it("确认与取消各发各的事件，父组件据此决定做不做", async () => {
    const wrapper = render();

    await wrapper.findComponent(ConfirmDialog).vm.$nextTick();
    document.body.querySelector<HTMLButtonElement>(".admin-btn.danger")!.click();
    expect(wrapper.emitted("confirm")).toHaveLength(1);

    document.body.querySelector<HTMLButtonElement>(".admin-btn-ghost")!.click();
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });

  it("关闭弹窗（× 或 Esc）按取消处理，不当成确认", async () => {
    const wrapper = render();

    document.body.querySelector<HTMLButtonElement>(".close")!.click();
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted("cancel")).toHaveLength(1);
    expect(wrapper.emitted("confirm")).toBeUndefined();
    wrapper.unmount();
  });

  it("提交中两个按钮一起禁用，防止连点删两次", () => {
    const wrapper = render({ busy: true });

    const buttons = document.body.querySelectorAll<HTMLButtonElement>(
      ".admin-btn.danger, .admin-btn-ghost",
    );
    expect(buttons).toHaveLength(2);
    expect([...buttons].every((b) => b.disabled)).toBe(true);
    wrapper.unmount();
  });

  it("红色主按钮只在确认弹窗里出现", () => {
    const wrapper = render();

    expect(document.body.querySelectorAll(".danger")).toHaveLength(1);
    wrapper.unmount();
  });

  it("confirmDisabled 只禁确认按钮，取消仍可用并发出 cancel", async () => {
    const wrapper = render({ confirmDisabled: true });
    await wrapper.vm.$nextTick();

    const confirm = document.body.querySelector<HTMLButtonElement>(".admin-btn.danger")!;
    const cancel = document.body.querySelector<HTMLButtonElement>(".admin-btn-ghost")!;
    expect(confirm.disabled).toBe(true);
    expect(cancel.disabled).toBe(false);
    cancel.click();
    expect(wrapper.emitted("cancel")).toHaveLength(1);
    wrapper.unmount();
  });
});
