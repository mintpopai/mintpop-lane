import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AirportResponse } from "../api/types";
import AirportsView from "./AirportsView.vue";

const listAirports = vi.fn<() => Promise<AirportResponse[]>>();
const deleteAirport = vi.fn(async () => undefined);

vi.mock("../api", () => ({ adminApi: () => ({ listAirports, deleteAirport }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

function airport(overrides: Partial<AirportResponse> = {}): AirportResponse {
  return {
    id: 1,
    name: "泰山云",
    websiteUrl: "https://taishan.example.com",
    remark: null,
    subscriptionCount: 2,
    primaryUsed: 7,
    primaryCapacity: 30,
    createdAt: "2026-09-28T00:00:00Z",
    updatedAt: "2026-09-28T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  document.body.innerHTML = "";
});

describe("AirportsView", () => {
  it("列出机场名、可点击的地址、订阅数与主用名额「已用 / 总容量」", async () => {
    listAirports.mockResolvedValue([airport()]);
    const wrapper = mount(AirportsView, { attachTo: document.body });
    await flushPromises();

    expect(wrapper.text()).toContain("泰山云");
    expect(wrapper.find('a[href="https://taishan.example.com"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("7 / 30");
  });

  it("没有机场时提示先建机场", async () => {
    listAirports.mockResolvedValue([]);
    const wrapper = mount(AirportsView, { attachTo: document.body });
    await flushPromises();

    expect(wrapper.text()).toContain("还没有机场");
  });
});
