import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import KeyValueEditor from "./KeyValueEditor.vue";
import type { KeyValueRow } from "../utils/nodeForm";

const 初始行: KeyValueRow[] = [{ key: "sni", value: "a.com" }];

describe("KeyValueEditor", () => {
  it("点新增会追加一行空的键值对", async () => {
    const wrapper = mount(KeyValueEditor, { props: { modelValue: 初始行 } });

    await wrapper.get("[data-test=add-row]").trigger("click");

    const emitted = wrapper.emitted("update:modelValue");
    expect(emitted?.at(-1)?.[0]).toEqual([{ key: "sni", value: "a.com" }, { key: "", value: "" }]);
  });

  it("点删除会移除对应行", async () => {
    const wrapper = mount(KeyValueEditor, { props: { modelValue: 初始行 } });

    await wrapper.get("[data-test=remove-row-0]").trigger("click");

    expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toEqual([]);
  });
});
