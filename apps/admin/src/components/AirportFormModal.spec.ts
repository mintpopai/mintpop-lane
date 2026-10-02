import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AirportFormModal from "./AirportFormModal.vue";

const createAirport = vi.fn(async () => 1);
const updateAirport = vi.fn(async () => undefined);
const showToast = vi.fn();

vi.mock("../api", () => ({ adminApi: () => ({ createAirport, updateAirport }) }));
vi.mock("../toast", () => ({ showToast: (...args: unknown[]) => showToast(...args) }));

function query(selector: string): DOMWrapper<Element> {
  const el = document.querySelector(selector);
  if (!el) {
    throw new Error(`未找到元素：${selector}`);
  }
  return new DOMWrapper(el);
}

function submitButton(): DOMWrapper<Element> {
  return new DOMWrapper(Array.from(document.querySelectorAll("button.admin-btn")).at(-1)!);
}

beforeEach(() => {
  vi.clearAllMocks();
  // jsdom 不实现 scrollIntoView，AdminSelect 展开面板定位高亮项时会调它
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("AirportFormModal", () => {
  it("新建：名称去首尾空白后提交", async () => {
    const wrapper = mount(AirportFormModal, { attachTo: document.body, props: { editing: null } });
    await query("#airport-name").setValue(" 泰山云 ");
    await query("#airport-url").setValue("https://taishan.example.com");
    await submitButton().trigger("click");

    await vi.waitFor(() =>
      expect(createAirport).toHaveBeenCalledWith({
        name: "泰山云",
        websiteUrl: "https://taishan.example.com",
        remark: "",
        primaryEnabled: true,
      }),
    );
    expect(wrapper.emitted("saved")).toBeTruthy();
  });

  it("名称为空不提交", async () => {
    mount(AirportFormModal, { attachTo: document.body, props: { editing: null } });
    await submitButton().trigger("click");

    expect(showToast).toHaveBeenCalledWith("error", "填写机场名称");
    expect(createAirport).not.toHaveBeenCalled();
  });

  it("编辑：回显「仅备用」，改为主用机场后提交 primaryEnabled=true", async () => {
    mount(AirportFormModal, {
      attachTo: document.body,
      props: {
        editing: {
          id: 7,
          name: "备用云",
          websiteUrl: null,
          remark: null,
          primaryEnabled: false,
          subscriptionCount: 1,
          primaryUsed: 0,
          primaryCapacity: 0,
          createdAt: "2026-10-01T00:00:00Z",
          updatedAt: "2026-10-01T00:00:00Z",
        },
      },
    });
    expect(query("#airport-primary").text()).toContain("仅备用");

    await query("#airport-primary").trigger("click");
    const option = Array.from(document.querySelectorAll('[role="option"]')).find((el) =>
      el.textContent?.includes("主用机场"),
    );
    await new DOMWrapper(option!).trigger("click");
    await submitButton().trigger("click");

    await vi.waitFor(() =>
      expect(updateAirport).toHaveBeenCalledWith(7, {
        name: "备用云",
        websiteUrl: "",
        remark: "",
        primaryEnabled: true,
      }),
    );
  });
});
