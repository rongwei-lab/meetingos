import { describe, expect, it } from "vitest";
import { LatestRequestRegistry, RequestRuntime } from "../src/utils/requestControl";

describe("LatestRequestRegistry", () => {
  it("只允许同一 key 的最后一代请求写回", () => {
    const registry = new LatestRequestRegistry();
    const first = registry.begin("meeting-detail");
    const second = registry.begin("meeting-detail");

    expect(first.signal.aborted).toBe(true);
    expect(registry.isCurrent(first)).toBe(false);
    expect(registry.isCurrent(second)).toBe(true);
  });

  it("cancel 会立即使当前请求失效", () => {
    const registry = new LatestRequestRegistry();
    const request = registry.begin("attendee-search:1");

    registry.cancel("attendee-search:1");

    expect(request.signal.aborted).toBe(true);
    expect(registry.isCurrent(request)).toBe(false);
  });
});

describe("RequestRuntime", () => {
  it("reset 同时中止请求并清空所有注册的 single-flight 缓存", () => {
    const runtime = new RequestRuntime();
    const cacheA = runtime.createPromiseCache<string, Promise<void>>();
    const cacheB = runtime.createPromiseCache<string, Promise<void>>();
    cacheA.set("meeting:1", Promise.resolve());
    cacheB.set("meeting:2", Promise.resolve());
    const request = runtime.requests.begin("statistics:1:2026-07-01:2026-07-07");

    runtime.reset();

    expect(cacheA.size).toBe(0);
    expect(cacheB.size).toBe(0);
    expect(request.signal.aborted).toBe(true);
    expect(runtime.requests.activeRequestCount).toBe(0);
  });
});
