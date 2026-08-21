/**
 * 运行时接入配置。
 *
 * 这些值刻意**不进构建产物**：镜像由 CI 构建，那里不该知道生产环境的部署细节；
 * 而 `docker build` 也不允许依赖外部喂进来的参数。于是配置改由站点根目录的
 * /config.json 提供，部署时用 compose 以只读卷挂进 nginx 的静态目录。
 *
 * 管理端不再直连 Logto，唯一需要的运行时配置是接口前缀。
 */
export interface RuntimeConfig {
  /** 管理接口前缀。同域分路径部署时是 /api */
  apiBaseUrl: string;
}

const requiredKeys: Array<keyof RuntimeConfig> = ["apiBaseUrl"];

let cached: RuntimeConfig | null = null;

/** 拉取并校验 /config.json。应用挂载之前调用一次 */
export async function loadRuntimeConfig(
  fetchImpl: typeof fetch = (input, init) => globalThis.fetch(input, init),
): Promise<RuntimeConfig> {
  const response = await fetchImpl("/config.json", { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`读取 /config.json 失败（HTTP ${response.status}）：部署时需把宿主上的 admin-config.json 挂到站点根目录的 config.json`);
  }

  const raw = (await response.json()) as Partial<RuntimeConfig> | null;
  for (const key of requiredKeys) {
    if (!raw?.[key]) {
      throw new Error(`/config.json 缺少必填项 ${key}`);
    }
  }

  cached = raw as RuntimeConfig;
  return cached;
}

/** 同步取用已加载的配置。加载前调用会直接抛错，不给出半初始化的对象 */
export function runtimeConfig(): RuntimeConfig {
  if (!cached) {
    throw new Error("运行时配置尚未加载，应用启动流程有误");
  }
  return cached;
}

/** 仅供测试用：清掉缓存 */
export function resetRuntimeConfig(): void {
  cached = null;
}
