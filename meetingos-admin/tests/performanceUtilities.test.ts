import { computed, effectScope, nextTick, ref } from "vue";
import { describe, expect, it } from "vitest";
import { useClientPagination } from "../src/composables/useClientPagination";
import { mapWithConcurrency } from "../src/utils/async";

describe("前端性能工具", () => {
  it("受控并发保持结果顺序且不会超过上限", async () => {
    let active = 0;
    let peak = 0;
    const result = await mapWithConcurrency([1, 2, 3, 4, 5, 6, 7], 3, async (value) => {
      active += 1;
      peak = Math.max(peak, active);
      await new Promise((resolve) => setTimeout(resolve, value % 2 ? 4 : 1));
      active -= 1;
      return value * 10;
    });

    expect(result).toEqual([10, 20, 30, 40, 50, 60, 70]);
    expect(peak).toBe(3);
  });

  it("移动分页只暴露当前切片并在筛选变化后回到第一页", async () => {
    const scope = effectScope();
    const source = ref(Array.from({ length: 45 }, (_, index) => index + 1));
    const filterKey = ref("all");
    const pagination = scope.run(() => useClientPagination(computed(() => source.value), 20, filterKey));
    if (!pagination) throw new Error("分页初始化失败");

    pagination.page.value = 3;
    expect(pagination.visibleItems.value).toEqual([41, 42, 43, 44, 45]);

    source.value = source.value.filter((value) => value <= 8);
    filterKey.value = "filtered";
    await nextTick();

    expect(pagination.page.value).toBe(1);
    expect(pagination.pageCount.value).toBe(1);
    expect(pagination.visibleItems.value).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    scope.stop();
  });
});
