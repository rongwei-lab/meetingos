import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { http } from "../src/api/http";
import {
  clearMeetingRuntimeCaches,
  useMeetingStore,
  type StatisticsDashboardComponentDto,
  type StatisticsDashboardQueryResultDto
} from "../src/stores/meeting";

function envelope<T>(data: T) {
  return {
    data: {
      success: true,
      code: "OK",
      message: "success",
      timestamp: "2026-07-25T10:00:00Z",
      data
    }
  } as never;
}

function installLocalStorage() {
  const values = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    clear: () => values.clear()
  });
}

function component(id = "registration-count"): StatisticsDashboardComponentDto {
  return {
    id,
    title: "报名人数",
    fieldKey: "system:attendee",
    aggregation: "COUNT",
    visualization: "NUMBER",
    width: "HALF",
    filters: []
  };
}

describe("statistics dashboard store", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearMeetingRuntimeCaches();
    installLocalStorage();
    setActivePinia(createPinia());
  });

  it("loads field catalog and default config, then submits all widgets in one query", async () => {
    const get = vi.spyOn(http, "get").mockImplementation(async (url) => {
      if (url === "/admin/statistics/dashboard/fields") {
        return envelope({
          fields: [{
            key: "tduck:inviter",
            label: "邀请人",
            category: "报名表字段",
            dataType: "TEXT",
            allowedAggregations: ["COUNT", "DISTRIBUTION"],
            allowedVisualizations: ["NUMBER", "BAR", "PIE", "TABLE"],
            allowedOperators: ["EQUALS", "IN", "IS_EMPTY"]
          }]
        });
      }
      if (url === "/admin/statistics/dashboard/config") {
        return envelope({ meetingId: 9, components: [component()], updatedAt: "2026-07-25T10:00:00Z" });
      }
      throw new Error(`unexpected GET ${url}`);
    });
    const post = vi.spyOn(http, "post").mockResolvedValue(envelope<StatisticsDashboardQueryResultDto>({
      components: [{ ...component(), value: 12, total: 12, items: [], status: "SUCCESS" }],
      generatedAt: "2026-07-25T10:01:00Z"
    }));
    const store = useMeetingStore();
    store.setCurrentMeetingId("9");

    const [fields, config] = await Promise.all([
      store.loadStatisticsDashboardFields("9"),
      store.loadStatisticsDashboardConfig("9")
    ]);
    const result = await store.queryStatisticsDashboard("9", config!.components, {
      startDate: "2026-07-19",
      endDate: "2026-07-25"
    });

    expect(fields[0]).toMatchObject({ key: "tduck:inviter", label: "邀请人" });
    expect(store.currentStatisticsDashboardConfig?.components).toHaveLength(1);
    expect(store.currentStatisticsDashboardQuery?.components[0]?.value).toBe(12);
    expect(result?.components[0]?.value).toBe(12);
    expect(get).toHaveBeenCalledWith("/admin/statistics/dashboard/fields", expect.objectContaining({ params: { meetingId: 9 } }));
    expect(get).toHaveBeenCalledWith("/admin/statistics/dashboard/config", expect.objectContaining({ params: { meetingId: 9 } }));
    expect(post).toHaveBeenCalledWith(
      "/admin/statistics/dashboard/query",
      expect.objectContaining({
        meetingId: 9,
        components: [component()],
        startDate: "2026-07-19",
        endDate: "2026-07-25"
      }),
      expect.objectContaining({ signal: expect.any(AbortSignal) })
    );
  });

  it("saving a config invalidates a query that started with the previous layout", async () => {
    let resolvePreviousQuery: ((value: ReturnType<typeof envelope<StatisticsDashboardQueryResultDto>>) => void) | undefined;
    const previousResult = new Promise<ReturnType<typeof envelope<StatisticsDashboardQueryResultDto>>>((resolve) => {
      resolvePreviousQuery = resolve;
    });
    vi.spyOn(http, "post").mockReturnValue(previousResult as never);
    vi.spyOn(http, "put").mockResolvedValue(envelope({ meetingId: 9, components: [component("saved")], updatedAt: "2026-07-25T10:02:00Z" }));
    const store = useMeetingStore();
    store.setCurrentMeetingId("9");

    const previousQuery = store.queryStatisticsDashboard("9", [component("previous")], {});
    await store.saveStatisticsDashboardConfig("9", [component("saved")]);
    resolvePreviousQuery?.(envelope({
      components: [{ ...component("previous"), value: 99, items: [], status: "SUCCESS" }]
    }));

    await expect(previousQuery).resolves.toBeNull();
    expect(store.currentStatisticsDashboardConfig?.components.map((item) => item.id)).toEqual(["saved"]);
    expect(store.currentStatisticsDashboardQuery).toBeNull();
  });

  it("defaults legacy dashboard components to half width and preserves full width", async () => {
    vi.spyOn(http, "get").mockResolvedValue(envelope({
      meetingId: 9,
      components: [
        { ...component("legacy"), width: undefined },
        { ...component("wide"), width: "FULL" }
      ]
    }));
    const store = useMeetingStore();
    store.setCurrentMeetingId("9");

    const config = await store.loadStatisticsDashboardConfig("9");

    expect(config?.components.map((item) => item.width)).toEqual(["HALF", "FULL"]);
  });
});
