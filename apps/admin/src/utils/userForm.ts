import { FRONT_ACTION } from "../api/types";
import type {
  AdminNodeResponse,
  AdminUserResponse,
  FrontAction,
  UserSaveRequest,
  UserStatus,
} from "../api/types";

/**
 * 第一跳下拉里那个「自动分配」档位。它不是节点 id，而是一个动作，
 * 与具体节点 id、以及表示「不分配」的 null 并列成三态。
 */
export const FRONT_SELECTION = {
  AUTO_ALLOCATE: "AUTO_ALLOCATE",
} as const;

/** 第一跳下拉的取值：节点 id / null＝不分配 / AUTO_ALLOCATE＝按故障域自动分配一组 */
export type FrontSelection = number | null | (typeof FRONT_SELECTION)[keyof typeof FRONT_SELECTION];

export interface UserFormModel {
  id: number;
  status: UserStatus;
  /**
   * 这次保存要对第一跳做什么。userToForm 一律回填 KEEP——改备注、改状态这些保存
   * 绝不该顺手动别人的前置组；只有链路卡上真的操作了第一跳下拉才会变成别的值。
   */
  frontAction: FrontAction;
  /** 第一跳主节点 id，只在 frontAction 为 PIN 时有意义，其余三态一律 null */
  frontNodeId: number | null;
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
    // KEEP 下服务端忽略 frontNodeId，这里就回填 null，让「这个字段此刻无意义」在数据上也成立：
    // 回填用户当前的主节点只会让下一个人以为它被用到了，而那个值还可能是过期快照
    frontNodeId: null,
    landNodeId: user.landNodeId,
    // 服务端没写备注时回 null，输入框要的是空串
    remark: user.remark ?? "",
  };
}

/**
 * 把第一跳下拉的三态取值翻译成接口上的两个字段。
 * 下拉的三个档位与服务端四态里的三个一一对应；第四态 KEEP 不在下拉里——
 * 它表示「这次压根没碰这个下拉」，由调用方（详情页的 frontTouched）决定，见 UserDetailView。
 */
export function frontSelectionToPayload(
  selection: FrontSelection,
): Pick<UserFormModel, "frontAction" | "frontNodeId"> {
  if (selection === FRONT_SELECTION.AUTO_ALLOCATE) {
    return { frontAction: FRONT_ACTION.AUTO, frontNodeId: null };
  }
  if (selection === null) {
    return { frontAction: FRONT_ACTION.CLEAR, frontNodeId: null };
  }
  return { frontAction: FRONT_ACTION.PIN, frontNodeId: selection };
}

export function buildUserPayload(form: UserFormModel): UserSaveRequest {
  return {
    status: form.status,
    frontAction: form.frontAction,
    // 下拉清空时可能产出 undefined 而非 null，这里统一收成 null，
    // 让「未分配」在接口上只有一种表示，类型契约才与实际下发的 JSON 一致
    frontNodeId: form.frontNodeId ?? null,
    landNodeId: form.landNodeId ?? null,
    remark: form.remark.trim(),
  };
}

/** 可分配的第一跳节点：角色对、且启用 */
export function selectableFrontNodes(nodes: AdminNodeResponse[]): AdminNodeResponse[] {
  return nodes.filter((node) => node.role === "FRONT" && node.status === "ENABLED");
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
