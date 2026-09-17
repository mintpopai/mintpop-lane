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
    // 断言 input.element.value === "" 是恒真的：用例里 files 是靠 Object.defineProperty 伪造的，
    // 没走真实文件选择流程，jsdom 里 value 本来就一直是 ""——不管组件有没有清空都会通过。
    // 改为监视 value 的 setter，断言组件确实主动调用过它，才能证明「代码清空了」而非「代码没碰过」。
    uploadImage.mockResolvedValue("https://x/a.png");
    const wrapper = mount(ImageUploadButton);
    const setValue = vi.spyOn(HTMLInputElement.prototype, "value", "set");

    await pick(wrapper, file(1024));

    expect(setValue).toHaveBeenCalledWith("");
    setValue.mockRestore();
  });
});
