import { ref } from "vue";

export interface Toast {
  type: "success" | "error";
  text: string;
}

/** 全局 toast：App.vue 渲染，各视图调 showToast（替代 Element Plus 的 ElMessage） */
export const toast = ref<Toast | null>(null);
let timer: ReturnType<typeof setTimeout> | undefined;

export function showToast(type: Toast["type"], text: string): void {
  toast.value = { type, text };
  clearTimeout(timer);
  timer = setTimeout(() => {
    toast.value = null;
  }, 3000);
}
