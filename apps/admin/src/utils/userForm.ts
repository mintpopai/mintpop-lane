import { FRONT_ACTION } from "../api/types";
import type {
  AdminNodeResponse,
  AdminUserResponse,
  FrontAction,
  UserSaveRequest,
  UserStatus,
} from "../api/types";

/**
 * 第一跳下拉里那个「自动分配」档位。它是一个动作，与表示「不分配」的 null 并列成两态。
 * 第一跳不开放手工指定节点：节点随订阅刷新会增减，手工钉死的用户跟不上变化。
 */
export const FRONT_SELECTION = {
  AUTO_ALLOCATE: "AUTO_ALLOCATE",
} as const;

/** 第一跳下拉的取值：null＝不分配 / AUTO_ALLOCATE＝按故障域自动分配一组 */
export type FrontSelection = null | (typeof FRONT_SELECTION)[keyof typeof FRONT_SELECTION];

export interface UserFormModel {
  id: number;
  status: UserStatus;
  /**
   * 这次保存要对第一跳做什么。userToForm 一律回填 KEEP——改备注、改状态这些保存
   * 绝不该顺手动别人的前置组；只有链路卡上真的操作了第一跳下拉才会变成别的值。
   */
  frontAction: FrontAction;
  landNodeId: number | null;
  /**
   * 管理员自用说明。刻意设成必填而不是可选：更新接口是整体保存，
   * 处置态那几处快捷操作（停用/恢复/吊销）也走它——字段必填，TypeScript
   * 就会逼着每个调用点把现值带回来，「停用一下备注被清空」在编译期就发生不了。
   */
  remark: string;
}

export function userToForm(user: AdminUserResponse): UserFormModel {
  return {
    id: user.id,
    status: user.status,
    // 第一跳的处置是一次性意图、不是用户身上的状态，回填永远是「这次没动它」
    frontAction: FRONT_ACTION.KEEP,
    landNodeId: user.landNodeId,
    // 服务端没写备注时回 null，输入框要的是空串
    remark: user.remark ?? "",
  };
}

/**
 * 把第一跳下拉的两态取值翻译成接口上的处置。
 * 下拉的两个档位与服务端三态里的两个一一对应；第三态 KEEP 不在下拉里——
 * 它表示「这次压根没碰这个下拉」，由调用方（详情页的 frontTouched）决定，见 UserDetailView。
 */
export function frontSelectionToAction(selection: FrontSelection): FrontAction {
  return selection === FRONT_SELECTION.AUTO_ALLOCATE ? FRONT_ACTION.AUTO : FRONT_ACTION.CLEAR;
}

export function buildUserPayload(form: UserFormModel): UserSaveRequest {
  return {
    status: form.status,
    frontAction: form.frontAction,
    // 下拉清空时可能产出 undefined 而非 null，这里统一收成 null，
    // 让「未分配」在接口上只有一种表示，类型契约才与实际下发的 JSON 一致
    landNodeId: form.landNodeId ?? null,
    remark: form.remark.trim(),
  };
}

/**
 * 可分配的落地节点：角色对、启用、且还有剩余容量（已绑人数 < 容量）。
 * 当前用户自己绑着的那个要保留（即使已满），否则编辑时下拉框里会看不到自己已选的值。
 */
export function selectableLandNodes(
  nodes: AdminNodeResponse[],
  currentLandNodeId: number | null,
): AdminNodeResponse[] {
  return nodes.filter(
    (node) =>
      node.role === "LAND" &&
      node.status === "ENABLED" &&
      ((node.assignedUserCount ?? 0) < (node.capacity ?? 0) || node.id === currentLandNodeId),
  );
}
