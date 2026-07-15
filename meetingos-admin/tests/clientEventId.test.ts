import { describe, expect, it } from "vitest";
import { createClientEventId } from "../src/utils/clientEventId";

describe("邀请事件客户端 UUID", () => {
  it("生成可被后端 UUID 解析的 v4 标识", () => {
    expect(createClientEventId()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i);
  });
});
