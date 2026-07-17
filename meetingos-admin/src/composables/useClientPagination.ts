import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";

export function useClientPagination<T>(
  source: MaybeRefOrGetter<readonly T[]>,
  pageSize = 20,
  resetKey?: MaybeRefOrGetter<unknown>
) {
  const normalizedPageSize = Math.max(1, Math.floor(pageSize) || 1);
  const page = ref(1);
  const items = computed(() => toValue(source));
  const pageCount = computed(() => Math.max(1, Math.ceil(items.value.length / normalizedPageSize)));
  const visibleItems = computed(() => {
    const start = (page.value - 1) * normalizedPageSize;
    return items.value.slice(start, start + normalizedPageSize);
  });

  if (resetKey !== undefined) {
    // 只在会议或筛选语义变化时复位；普通编辑刷新应保留用户正在查看的页码。
    watch(() => toValue(resetKey), () => {
      page.value = 1;
    }, { deep: true });
  }

  watch(pageCount, (count) => {
    if (page.value > count) page.value = count;
  });

  return {
    page,
    pageCount,
    pageSize: normalizedPageSize,
    itemCount: computed(() => items.value.length),
    visibleItems
  };
}
