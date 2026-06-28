import { onBeforeUnmount, onMounted, ref } from "vue";

export const MOBILE_BREAKPOINT = 800;

/**
 * 后台管理端的移动端断点统一放在这里，避免不同页面各自写一套宽度判断。
 * 服务端渲染不在当前项目中使用，但这里仍做 window 判空，方便后续测试或预渲染复用。
 */
export function useIsMobile(breakpoint = MOBILE_BREAKPOINT) {
  const isMobile = ref(false);

  const update = () => {
    isMobile.value = typeof window !== "undefined" && window.innerWidth <= breakpoint;
  };

  onMounted(() => {
    update();
    window.addEventListener("resize", update, { passive: true });
  });

  onBeforeUnmount(() => {
    window.removeEventListener("resize", update);
  });

  return isMobile;
}
