import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import KeyValueEditor from "./KeyValueEditor.vue";
import type { KeyValueRow } from "../utils/nodeForm";

const initialRows: KeyValueRow[] = [{ key: "sni", value: "a.com" }];

describe("KeyValueEditor", () => {
  it("点新增会追加一行空的键值对", async () => {
    const wrapper = mount(KeyValueEditor, { props: { modelValue: initialRows } });

    await wrapper.get("[data-test=add-row]").trigger("click");

    const emitted = wrapper.emitted("update:modelValue");
    expect(emitted?.at(-1)?.[0]).toEqual([{ key: "sni", value: "a.com" }, { key: "", value: "" }]);
  });

  it("改动某一行的值会带着完整列表发出更新", async () => {
    const wrapper = mount(KeyValueEditor, {
      props: {
        modelValue: [
          { key: "sni", value: "a.com" },
          { key: "udp", value: "true" },
        ],
      },
    });

    // 输入框顺序是 [键0, 值0, 键1, 值1]，下标 1 即第一行的值
    await wrapper.findAll("input")[1].setValue("b.com");

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toEqual([
      { key: "sni", value: "b.com" },
      { key: "udp", value: "true" },
    ]);
  });

  it("点删除会移除对应行", async () => {
    const wrapper = mount(KeyValueEditor, { props: { modelValue: initialRows } });

    await wrapper.get("[data-test=remove-row-0]").trigger("click");

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toEqual([]);
  });
});
