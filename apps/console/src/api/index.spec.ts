import { describe, expect, it } from "vitest";
import { authApi, consoleApi } from "./index";

describe("api 装配层", () => {
  it("两个 api 都是单例", () => {
    expect(authApi()).toBe(authApi());
    expect(consoleApi()).toBe(consoleApi());
  });
});
