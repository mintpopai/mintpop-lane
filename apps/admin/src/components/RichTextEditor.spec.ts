import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import RichTextEditor from "./RichTextEditor.vue";

vi.mock("../api", () => ({ adminApi: () => ({ uploadImage: vi.fn() }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

/** TipTap 建编辑器是异步的，等一帧让它挂好 */
async function nextFrame() {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe("RichTextEditor", () => {
  it("把传入的 HTML 渲染进编辑区", async () => {
    const wrapper = mount(RichTextEditor, {
      attachTo: document.body,
      props: { id: "plan-detail", modelValue: "<p>含 5 个并发席位</p>" },
    });
    await nextFrame();

    expect(wrapper.get(".editor-surface").html()).toContain("含 5 个并发席位");
    wrapper.unmount();
  });

  it("外部把内容改成与当前一致时不重建文档，光标不会被打回开头", async () => {
    const wrapper = mount(RichTextEditor, {
      attachTo: document.body,
      props: { id: "plan-detail", modelValue: "<p>原文</p>" },
    });
    await nextFrame();
    const editor = (wrapper.vm as unknown as { editor: { commands: { setContent: unknown } } })
      .editor;
    const setContent = vi.spyOn(
      editor.commands as unknown as { setContent: () => boolean },
      "setContent",
    );

    await wrapper.setProps({ modelValue: "<p>原文</p>" });

    expect(setContent).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
