/**
 * 出口 IP → IANA 时区的 GeoIP 查询。
 * 走 ipwho.is（免密钥、HTTPS、支持 CORS），只取 timezone.id 一个字段。
 * 任何失败（网络不通、被墙、限流、查不到）一律返回 null——预填是锦上添花，
 * 失败就降级为管理员人工填写，绝不阻塞表单。
 */
export async function lookupIpTimezone(ip: string, fetchFn: typeof fetch = fetch): Promise<string | null> {
  try {
    const response = await fetchFn(`https://ipwho.is/${encodeURIComponent(ip)}?fields=success,timezone.id`);
    if (!response.ok) {
      return null;
    }
    const body: unknown = await response.json();
    if (typeof body !== "object" || body === null || (body as { success?: unknown }).success !== true) {
      return null;
    }
    const timezone = (body as { timezone?: unknown }).timezone;
    if (typeof timezone !== "object" || timezone === null) {
      return null;
    }
    const id = (timezone as { id?: unknown }).id;
    return typeof id === "string" && id !== "" ? id : null;
  } catch {
    return null;
  }
}
