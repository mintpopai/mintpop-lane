import { mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { showToast } from "../toast";
import ImageUploadButton from "./ImageUploadButton.vue";

const uploadImage = vi.fn<(file: File) => Promise<string>>();

vi.mock("../api", () => ({ adminApi: () => ({ uploadImage }) }));
vi.mock("../toast", () => ({ showToast: vi.fn() }));

/** 造一个指定字节数的假文件：只有 size 参与本组件的判断 */
function file(bytes: number): File {
  const f = new File(["x"], "a.png", { type: "image/png" });
  Object.defineProperty(f, "size", { value: bytes });
  return f;
}

/** 把文件塞进隐藏的 input 并触发 change——用例里没法真的弹系统选择器 */
async function pick(wrapper: ReturnType<typeof mount>, picked: File | null) {
  const input = wrapper.get<HTMLInputElement>("input[type=file]");
  Object.defineProperty(input.element, "files", {
    value: picked ? [picked] : [],
    configurable: true,
  });
  await input.trigger("change");
}

describe("ImageUploadButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("上传成功后抛出服务端返回的地址", async () => {
    uploadImage.mockResolvedValue("https://assets.lane.mintpop.ai/plans/2026/09/a.png");
    const wrapper = mount(ImageUploadButton);

    await pick(wrapper, file(1024));
    await vi.waitFor(() => expect(wrapper.emitted("uploaded")).toBeTruthy());

    expect(wrapper.emitted("uploaded")?.[0]).toEqual([
      "https://assets.lane.mintpop.ai/plans/2026/09/a.png",
    ]);
  });

  it("超过 5 MB 在本地就挡下，不发请求", async () => {
    const wrapper = mount(ImageUploadButton);

    await pick(wrapper, file(5 * 1024 * 1024 + 1));

    expect(uploadImage).not.toHaveBeenCalled();
    expect(showToast).toHaveBeenCalledWith("error", "图片不能超过 5 MB");
  });

  it("上传失败弹 toast，不抛 uploaded", async () => {
    uploadImage.mockRejectedValue(new Error("图片存储未配置"));
    const wrapper = mount(ImageUploadButton);

    await pick(wrapper, file(1024));
    await vi.waitFor(() => expect(showToast).toHaveBeenCalledWith("error", "图片存储未配置"));

    expect(wrapper.emitted("uploaded")).toBeFalsy();
  });

  it("选完就清空 input 的值，同一个文件能再选一次", async () => {
    uploadImage.mockResolvedValue("https://x/a.png");
    const wrapper = mount(ImageUploadButton);

    await pick(wrapper, file(1024));

    expect(wrapper.get<HTMLInputElement>("input[type=file]").element.value).toBe("");
  });
});
