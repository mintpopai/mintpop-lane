import { defineStore } from "pinia";
import { ref } from "vue";
import { adminApi } from "../api";
import { REBIND_REQUEST_STATUS } from "../api/types";

/**
 * 导航轨上「换机申请」的待办数。
 *
 * 全仓只有这一处破例用 store：本仓的规矩是「视图自持 ref」，前提是「谁显示、谁取数」。
 * 角标不满足——显示它的是 AppLayout，改变它的动作（同意 / 拒绝）在 DeviceRequestsView，
 * 两者没有父子关系，只能靠共享状态把「处理完立刻少一个」接上。
 *
 * 刻意不轮询：通知的主渠道是飞书（服务端在申请落库后推送），角标只是进后台时顺手看一眼。
 * 代价是新申请要等下次刷新页面或处理一次申请后才反映到角标，可接受。
 */
export const useRebindStore = defineStore("rebind", () => {
  const pendingCount = ref(0);

  /** 拉一次待办数 */
  async function refresh(): Promise<void> {
    try {
      const rows = await adminApi().listDeviceRebindRequests(REBIND_REQUEST_STATUS.PENDING);
      pendingCount.value = rows.length;
    } catch {
      // 失败静默、且保留原值：一个提示数字不该弹错误打断管理员手头的事，
      // 更不该清零——那等于把「有待办」说成「没待办」
    }
  }

  return { pendingCount, refresh };
});
