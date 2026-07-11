import { describe, expect, it } from "vitest";
import { buildImportIdempotencyKey, importTaskStatusText, pollFileTask } from "../src/utils/fileTasks";
import { filenameFromDisposition } from "../src/stores/meeting";

describe("文件任务工具", () => {
  it("同会议、类型和内容生成稳定幂等键，作用域变化时键也变化", async () => {
    const first = new File(["same-content"], "first.xlsx");
    const renamed = new File(["same-content"], "renamed.xlsx");

    const firstKey = await buildImportIdempotencyKey("12", "attendees", first);
    expect(await buildImportIdempotencyKey("12", "attendees", renamed)).toBe(firstKey);
    expect(await buildImportIdempotencyKey("13", "attendees", first)).not.toBe(firstKey);
    expect(await buildImportIdempotencyKey("12", "rooms", first)).not.toBe(firstKey);
  });

  it("终态任务不会继续发起轮询请求", async () => {
    let loadCount = 0;
    const completed = await pollFileTask(
      { status: "COMPLETED", id: "1" },
      async () => {
        loadCount += 1;
        return { status: "COMPLETED", id: "1" };
      },
      new Set(["COMPLETED"])
    );

    expect(completed.status).toBe("COMPLETED");
    expect(loadCount).toBe(0);
  });

  it("取消轮询后不再加载任务", async () => {
    const controller = new AbortController();
    let loadCount = 0;
    const polling = pollFileTask(
      { status: "RUNNING", id: "1" },
      async () => {
        loadCount += 1;
        return { status: "COMPLETED", id: "1" };
      },
      new Set(["COMPLETED"]),
      { signal: controller.signal, intervalMs: 1000 }
    );

    controller.abort();

    await expect(polling).rejects.toMatchObject({ name: "AbortError" });
    expect(loadCount).toBe(0);
  });

  it("导入状态使用文件格式检查文案", () => {
    const statusCopy = ["PENDING", "PREFLIGHTING", "PREVIEW_READY"].map(importTaskStatusText).join(" ");

    expect(statusCopy).toContain("文件格式检查");
    expect(statusCopy).not.toMatch(/预检|预检查/);
  });

  it("正确解析 RFC 5987 UTF-8 下载文件名", () => {
    expect(filenameFromDisposition("attachment; filename*=UTF-8''%E5%A4%B1%E8%B4%A5%E6%98%8E%E7%BB%86.xlsx", "fallback.xlsx"))
      .toBe("失败明细.xlsx");
    expect(filenameFromDisposition('attachment; filename="meetingos.xlsx"', "fallback.xlsx")).toBe("meetingos.xlsx");
  });
});
