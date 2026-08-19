import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AdminApi } from "../api/admin";
import { BizError, ForbiddenError } from "../api/http";
import { useAuthStore } from "./auth";

function 假api(listNodes: AdminApi["listNodes"]): AdminApi {
  return { listNodes } as unknown as AdminApi;
}

describe("useAuthStore.probeAdmin", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("管理接口调得通就认定是管理员", async () => {
    const store = useAuthStore();

    await store.probeAdmin(假api(vi.fn(async () => [])));

    expect(store.isAdmin).toBe(true);
  });

  it("被 403 就认定没有管理权限——这是唯一的判定依据，不看 JWT 里的任何 claim", async () => {
    const store = useAuthStore();

    await store.probeAdmin(
      假api(
        vi.fn(async () => {
          throw new ForbiddenError();
        }),
      ),
    );

    expect(store.isAdmin).toBe(false);
  });

  it("其它错误不当成没权限：网络抖动不该把人赶去「无管理权限」页", async () => {
    const store = useAuthStore();
    const 探测 = store.probeAdmin(
      假api(
        vi.fn(async () => {
          throw new BizError(110002, "服务内部错误");
        }),
      ),
    );

    await expect(探测).rejects.toBeInstanceOf(BizError);
    expect(store.isAdmin).toBeNull();
  });

  it("探过一次就不再重复探", async () => {
    const store = useAuthStore();
    const listNodes = vi.fn(async () => []);

    await store.probeAdmin(假api(listNodes));
    await store.probeAdmin(假api(listNodes));

    expect(listNodes).toHaveBeenCalledTimes(1);
  });
});
