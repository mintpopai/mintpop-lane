import { DOMWrapper, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AirportSubscriptionResponse } from "../api/types";
import AirportSubscriptionEditModal from "./AirportSubscriptionEditModal.vue";

const updateAirportSubscription = vi.fn(async () => undefined);
vi.mock("../api", () => ({ adminApi: () => ({ updateAirportSubscription }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

const subscription = {
  id: 3,
  name: "ts-01",
  airportId: 1,
  airportName: "泰山云",
  account: "a@x.com",
  bandwidthMbps: 300,
  primaryUsed: 4,
  primaryCapacity: 15,
  remark: null,
} as unknown as AirportSubscriptionResponse;

function query(selector: string): DOMWrapper<Element> {
  return new DOMWrapper(document.querySelector(selector)!);
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  document.body.innerHTML = "";
});

describe("AirportSubscriptionEditModal", () => {
  it("机场与带宽只读展示，提交只带名称、账号、备注", async () => {
    mount(AirportSubscriptionEditModal, { attachTo: document.body, props: { subscription } });

    expect(document.body.textContent).toContain("泰山云");
    expect(document.body.textContent).toContain("300 Mbps");
    expect(document.querySelector("#sub-edit-bandwidth")).toBeNull();

    await query("#sub-edit-account").setValue("b@x.com");
    await new DOMWrapper(Array.from(document.querySelectorAll("button.admin-btn")).at(-1)!).trigger(
      "click",
    );

    await vi.waitFor(() =>
      expect(updateAirportSubscription).toHaveBeenCalledWith(3, {
        name: "ts-01",
        account: "b@x.com",
        remark: "",
      }),
    );
  });
});
