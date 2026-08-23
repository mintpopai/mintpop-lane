import { describe, expect, it } from "vitest";
import { createAuthApi } from "./auth";
import type { HttpClient } from "./http";

describe("createAuthApi", () => {
  it("me 请求 /me 并原样返回数据", async () => {
    const calls: string[] = [];
    const http: HttpClient = {
      request: async <T>(path: string) => {
        calls.push(path);
        return { id: 1, email: "a@b.c", role: "ADMIN", subscriptions: [] } as T;
      },
    };
    const me = await createAuthApi(http).me();
    expect(calls).toEqual(["/me"]);
    expect(me.role).toBe("ADMIN");
  });
});
