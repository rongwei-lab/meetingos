import { onBeforeUnmount, ref } from "vue";
import { isRequestCanceled } from "@/api/http";
import { useMeetingStore, type ExportTaskDto } from "@/stores/meeting";
import { pollFileTask } from "@/utils/fileTasks";

const EXPORT_STOP_STATUSES = new Set(["COMPLETED", "FAILED", "EXPIRED"]);

export interface RunExportTaskOptions {
  startDate?: string;
  endDate?: string;
  onUpdate?: (task: ExportTaskDto) => void;
}

export function useExportTaskDownload() {
  const store = useMeetingStore();
  const exporting = ref(false);
  const currentTask = ref<ExportTaskDto | null>(null);
  let activeController: AbortController | null = null;
  let runGeneration = 0;

  async function runExport(type: string, options: RunExportTaskOptions = {}) {
    if (exporting.value) throw new Error("已有导出任务正在处理，请稍候");
    const meetingId = store.currentMeetingId;
    if (!meetingId) throw new Error("请先选择会议");

    activeController?.abort();
    const controller = new AbortController();
    const generation = ++runGeneration;
    activeController = controller;
    exporting.value = true;
    currentTask.value = null;
    try {
      let task = await store.createExportTask(type, {
        meetingId,
        startDate: options.startDate,
        endDate: options.endDate,
        signal: controller.signal
      });
      assertCurrentRun(controller, generation);
      currentTask.value = task;
      options.onUpdate?.(task);
      task = await pollFileTask(
        task,
        () => store.loadExportTask(task.taskId, { meetingId, signal: controller.signal }),
        EXPORT_STOP_STATUSES,
        {
          signal: controller.signal,
          onUpdate: (next) => {
            if (!isCurrentRun(controller, generation)) return;
            currentTask.value = next;
            options.onUpdate?.(next);
          }
        }
      );
      assertCurrentRun(controller, generation);
      currentTask.value = task;
      if (task.status !== "COMPLETED") {
        throw new Error(task.message || (task.status === "EXPIRED" ? "导出任务已过期，请重新创建" : "导出文件生成失败"));
      }
      if (task.fileAvailable !== true) {
        throw new Error(task.message || "导出任务已完成，但文件暂不可下载，请稍后在导出记录中重试");
      }
      await store.downloadExportTask(task.taskId, {
        meetingId,
        filename: task.filename,
        signal: controller.signal
      });
      assertCurrentRun(controller, generation);
      return task;
    } catch (error) {
      if (isRequestCanceled(error)) throw error;
      throw error;
    } finally {
      if (isCurrentRun(controller, generation)) {
        activeController = null;
        exporting.value = false;
      }
    }
  }

  function cancelAndReset() {
    runGeneration += 1;
    activeController?.abort();
    activeController = null;
    exporting.value = false;
    currentTask.value = null;
  }

  function isCurrentRun(controller: AbortController, generation: number) {
    return activeController === controller && !controller.signal.aborted && runGeneration === generation;
  }

  function assertCurrentRun(controller: AbortController, generation: number) {
    if (!isCurrentRun(controller, generation)) throw new DOMException("导出任务已取消", "AbortError");
  }

  const cancelExport = cancelAndReset;

  onBeforeUnmount(cancelAndReset);

  return { exporting, currentTask, runExport, cancelExport, cancelAndReset };
}
