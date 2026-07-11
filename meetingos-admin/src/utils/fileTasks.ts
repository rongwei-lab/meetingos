export interface FileTaskLike {
  status: string;
}

export interface PollFileTaskOptions<T extends FileTaskLike> {
  signal?: AbortSignal;
  intervalMs?: number;
  timeoutMs?: number;
  onUpdate?: (task: T) => void;
}

export async function buildImportIdempotencyKey(meetingId: string, type: string, file: File) {
  const context = new TextEncoder().encode(`meetingos-import-v1\n${meetingId}\n${type}\n`);
  const content = new Uint8Array(await file.arrayBuffer());
  const payload = new Uint8Array(context.length + content.length);
  payload.set(context);
  payload.set(content, context.length);

  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest("SHA-256", payload);
    return bytesToHex(new Uint8Array(digest));
  }

  // 旧浏览器兜底仍基于完整文件内容，确保同一会议、类型和文件得到稳定键。
  let hash = 0x811c9dc5;
  for (const byte of payload) {
    hash ^= byte;
    hash = Math.imul(hash, 0x01000193);
  }
  return `fallback-${(hash >>> 0).toString(16).padStart(8, "0")}-${file.size}`;
}

export async function pollFileTask<T extends FileTaskLike>(
  initialTask: T,
  loadTask: () => Promise<T>,
  stopStatuses: ReadonlySet<string>,
  options: PollFileTaskOptions<T> = {}
) {
  const intervalMs = options.intervalMs ?? 800;
  const timeoutMs = options.timeoutMs ?? 15 * 60_000;
  const startedAt = Date.now();
  let current = initialTask;
  options.onUpdate?.(current);

  while (!stopStatuses.has(current.status)) {
    if (Date.now() - startedAt >= timeoutMs) {
      throw new Error("任务处理时间过长，请稍后在任务记录中查看结果");
    }
    await abortableDelay(intervalMs, options.signal);
    current = await loadTask();
    options.onUpdate?.(current);
  }
  return current;
}

export function importTaskStatusText(status: string) {
  const labels: Record<string, string> = {
    PENDING: "等待文件格式检查",
    PREFLIGHTING: "正在检查文件格式",
    PREVIEW_READY: "文件格式检查完成，正在提交正式导入",
    RUNNING: "正在写入数据",
    COMPLETED: "导入完成",
    FAILED: "导入失败",
    EXPIRED: "任务已过期"
  };
  return labels[status] || status;
}

export function exportTaskStatusText(status: string) {
  const labels: Record<string, string> = {
    PENDING: "等待生成",
    RUNNING: "正在生成",
    COMPLETED: "已完成",
    FAILED: "失败",
    EXPIRED: "已过期"
  };
  return labels[status] || status;
}

function abortableDelay(delayMs: number, signal?: AbortSignal) {
  if (signal?.aborted) return Promise.reject(abortError());
  return new Promise<void>((resolve, reject) => {
    const timer = globalThis.setTimeout(() => {
      signal?.removeEventListener("abort", handleAbort);
      resolve();
    }, delayMs);
    const handleAbort = () => {
      globalThis.clearTimeout(timer);
      reject(abortError());
    };
    signal?.addEventListener("abort", handleAbort, { once: true });
  });
}

function abortError() {
  return new DOMException("任务轮询已取消", "AbortError");
}

function bytesToHex(bytes: Uint8Array) {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}
