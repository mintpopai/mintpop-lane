import { describe, expect, it, vi } from "vitest";
import { BizError, ForbiddenError, UnauthorizedError, createHttpClient } from "./http";

function 假fetch(status: number, body: unknown) {
  return vi.fn(
    async () =>
      ({
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
      }) as Response,
  );
}

function 建客户端(fetchMock: ReturnType<typeof 假fetch>) {
  return createHttpClient({
    baseUrl: "/api",
    getToken: async () => "token-abc",
    fetchImpl: fetchMock as unknown as typeof fetch,
  });
}

describe("createHttpClient", () => {
  it("成功时只把 data 交出去，调用方不用自己拆包", async () => {
    const fetchMock = 假fetch(200, { code: 0, data: { id: 7 }, msg: null });

    const data = await 建客户端(fetchMock).request<{ id: number }>("/admin/users");

    expect(data).toEqual({ id: 7 });
  });

  it("带上 Bearer token 与 JSON 头，并拼上 baseUrl", async () => {
    const fetchMock = 假fetch(200, { code: 0, data: null, msg: null });

    await 建客户端(fetchMock).request("/admin/nodes");

    // vi.fn 从零参的 假fetch 推断出空元组的调用签名，故先经 unknown 再断言
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/admin/nodes");
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer token-abc");
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });

  it("code 非 0 抛 BizError，带上业务码与服务端给的中文提示", async () => {
    const fetchMock = 假fetch(200, { code: 410002, data: null, msg: "该落地节点已被其他用户占用" });

    await expect(建客户端(fetchMock).request("/admin/users")).rejects.toMatchObject({
      name: "BizError",
      code: 410002,
      message: "该落地节点已被其他用户占用",
    });
    await expect(建客户端(fetchMock).request("/admin/users")).rejects.toBeInstanceOf(BizError);
  });

  it("403 是 Spring Security 的原生状态码，必须单独识别成「没有管理权限」", async () => {
    const fetchMock = 假fetch(403, null);

    await expect(建客户端(fetchMock).request("/admin/users")).rejects.toBeInstanceOf(ForbiddenError);
  });

  it("401 识别成登录失效，交给上层去重新登录", async () => {
    const fetchMock = 假fetch(401, null);

    await expect(建客户端(fetchMock).request("/admin/users")).rejects.toBeInstanceOf(UnauthorizedError);
  });

  it("其它非 2xx 状态不当成业务失败，直接报出状态码", async () => {
    const fetchMock = 假fetch(502, null);

    await expect(建客户端(fetchMock).request("/admin/users")).rejects.toThrow("502");
  });
});
