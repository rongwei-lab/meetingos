import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";

export function useClientPagination<T>(
  source: MaybeRefOrGetter<readonly T[]>,
  pageSize = 20,
  resetKey?: MaybeRefOrGetter<unknown>
) {
  const normalizedPageSize = Math.max(1, Math.floor(pageSize) || 1);
  const page = ref(1);
  const currentPageSize = ref(normalizedPageSize);
  const items = computed(() => toValue(source));
  const pageCount = computed(() => Math.max(1, Math.ceil(items.value.length / currentPageSize.value)));
  const visibleItems = computed(() => {
    const start = (page.value - 1) * currentPageSize.value;
    return items.value.slice(start, start + currentPageSize.value);
  });

  // 改变每页数量后从第一页开始，避免当前页超出新页数或让用户错过前面的记录。
  watch(currentPageSize, () => {
    page.value = 1;
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
    pageSize: currentPageSize,
    itemCount: computed(() => items.value.length),
    visibleItems
  };
}
