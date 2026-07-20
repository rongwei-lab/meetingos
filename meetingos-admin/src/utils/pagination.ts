/**
 * 所有分页入口共用相同的数量说明，避免不同页面只显示页码而缺少总量信息。
 */
export const PAGINATION_PAGE_SIZES = [10, 20, 50, 100];

export function paginationSummary({ itemCount, pageSize }: { itemCount?: number; pageSize: number }) {
  const total = typeof itemCount === "number" && Number.isFinite(itemCount) ? Math.max(0, itemCount) : 0;
  return `每页 ${pageSize} 条，共 ${total} 条`;
}
