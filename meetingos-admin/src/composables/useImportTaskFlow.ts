import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { useMessage, type UploadCustomRequestOptions } from "naive-ui";
import { isRequestCanceled } from "@/api/http";
import { useMeetingStore, type ImportTaskDto, type ImportTaskType } from "@/stores/meeting";
import { buildImportIdempotencyKey, pollFileTask } from "@/utils/fileTasks";

const PREFLIGHT_STOP_STATUSES = new Set(["PREVIEW_READY", "COMPLETED", "FAILED", "EXPIRED"]);
const IMPORT_STOP_STATUSES = new Set(["COMPLETED", "FAILED", "EXPIRED"]);

interface UseImportTaskFlowOptions {
  type: ImportTaskType;
  label: string;
  refresh: (meetingId: string) => Promise<void>;
}

export function useImportTaskFlow(options: UseImportTaskFlowOptions) {
  const store = useMeetingStore();
  const message = useMessage();
  const importing = ref(false);
  const uploadingFile = ref(false);
  const uploadProgress = ref(0);
  const selectedFileName = ref("");
  const task = ref<ImportTaskDto | null>(null);
  const flowError = ref("");
  const refreshWarning = ref("");
  const resultVisible = ref(false);
  const downloadingFailures = ref(false);
  let activeController: AbortController | null = null;
  let failureDownloadController: AbortController | null = null;
  let flowGeneration = 0;

  const hasFeedback = computed(() => importing.value || Boolean(task.value || flowError.value || selectedFileName.value));
  const progress = computed(() => {
    if (uploadingFile.value) return uploadProgress.value;
    return task.value?.progress || 0;
  });

  async function handleUpload(uploadOptions: UploadCustomRequestOptions) {
    if (importing.value) {
      uploadOptions.onError();
      message.warning(`${options.label}正在处理中，请等待当前任务完成`);
      return;
    }
    const file = uploadOptions.file.file;
    if (!file) {
      uploadOptions.onError();
      message.error("未读取到待导入文件");
      return;
    }

    const meetingId = store.currentMeetingId;
    if (!meetingId) {
      uploadOptions.onError();
      message.warning("请先选择会议");
      return;
    }

    activeController?.abort();
    const controller = new AbortController();
    const generation = ++flowGeneration;
    activeController = controller;
    importing.value = true;
    uploadingFile.value = true;
    uploadProgress.value = 0;
    selectedFileName.value = file.name;
    task.value = null;
    flowError.value = "";
    refreshWarning.value = "";
    resultVisible.value = false;

    try {
      const idempotencyKey = await buildImportIdempotencyKey(meetingId, options.type, file);
      assertCurrentRun(controller, generation);
      let current = await store.preflightImportTask(options.type, file, idempotencyKey, {
        meetingId,
        signal: controller.signal,
        onProgress: (percent) => {
          if (!isCurrentRun(controller, generation)) return;
          uploadProgress.value = percent;
          uploadOptions.onProgress({ percent });
        }
      });
      assertCurrentRun(controller, generation);
      uploadingFile.value = false;
      task.value = current;

      current = await pollFileTask(
        current,
        () => store.loadImportTask(current.id, { meetingId, signal: controller.signal }),
        PREFLIGHT_STOP_STATUSES,
        {
          signal: controller.signal,
          onUpdate: (next) => {
            if (isCurrentRun(controller, generation)) task.value = next;
          }
        }
      );
      assertCurrentRun(controller, generation);

      if (current.status === "PREVIEW_READY") {
        task.value = current;
        // 先让界面显示文件格式检查完成，再自动继续，避免用户误以为上传后直接写库。
        await nextTick();
        assertCurrentRun(controller, generation);
        current = await store.commitImportTask(current.id, { meetingId, signal: controller.signal });
        assertCurrentRun(controller, generation);
        task.value = current;
      }

      current = await pollFileTask(
        current,
        () => store.loadImportTask(current.id, { meetingId, signal: controller.signal }),
        IMPORT_STOP_STATUSES,
        {
          signal: controller.signal,
          onUpdate: (next) => {
            if (isCurrentRun(controller, generation)) task.value = next;
          }
        }
      );
      assertCurrentRun(controller, generation);
      task.value = current;
      resultVisible.value = true;

      if (current.status !== "COMPLETED") {
        uploadOptions.onError();
        flowError.value = current.message || (current.status === "EXPIRED" ? "导入任务已过期，请重新上传" : `${options.label}失败`);
        message.error(flowError.value);
        return;
      }

      uploadOptions.onFinish();
      try {
        await options.refresh(meetingId);
      } catch (error) {
        assertCurrentRun(controller, generation);
        refreshWarning.value = error instanceof Error ? error.message : "页面数据刷新失败";
        message.warning(`${options.label}已完成，但页面刷新失败，请手动刷新后查看最新数据`);
      }
      assertCurrentRun(controller, generation);

      const resultText = `${options.label}完成：成功 ${current.importedRows} 行，失败 ${current.failedRows} 行`;
      if (current.failedRows > 0) message.warning(resultText, { duration: 6000 });
      else message.success(resultText);
    } catch (error) {
      uploadOptions.onError();
      if (isRequestCanceled(error)) return;
      flowError.value = error instanceof Error ? error.message : `${options.label}失败`;
      resultVisible.value = true;
      message.error(flowError.value);
    } finally {
      if (isCurrentRun(controller, generation)) {
        activeController = null;
        uploadingFile.value = false;
        importing.value = false;
      }
    }
  }

  async function downloadFailures() {
    if (!task.value?.failureFileAvailable || downloadingFailures.value) return;
    const targetTask = task.value;
    const generation = flowGeneration;
    failureDownloadController?.abort();
    const controller = new AbortController();
    failureDownloadController = controller;
    downloadingFailures.value = true;
    try {
      await store.downloadImportFailures(targetTask.id, {
        meetingId: String(targetTask.meetingId),
        signal: controller.signal
      });
      if (controller.signal.aborted || generation !== flowGeneration || task.value?.id !== targetTask.id) return;
      message.success("失败明细 XLSX 已开始下载");
    } catch (error) {
      if (isRequestCanceled(error) || controller.signal.aborted || generation !== flowGeneration) return;
      message.error(error instanceof Error ? error.message : "失败明细下载失败");
    } finally {
      if (failureDownloadController === controller) {
        failureDownloadController = null;
        downloadingFailures.value = false;
      }
    }
  }

  function openResult() {
    resultVisible.value = true;
  }

  function cancelAndReset() {
    flowGeneration += 1;
    activeController?.abort();
    failureDownloadController?.abort();
    activeController = null;
    failureDownloadController = null;
    importing.value = false;
    uploadingFile.value = false;
    uploadProgress.value = 0;
    selectedFileName.value = "";
    task.value = null;
    flowError.value = "";
    refreshWarning.value = "";
    resultVisible.value = false;
    downloadingFailures.value = false;
  }

  function isCurrentRun(controller: AbortController, generation: number) {
    return activeController === controller && !controller.signal.aborted && flowGeneration === generation;
  }

  function assertCurrentRun(controller: AbortController, generation: number) {
    if (!isCurrentRun(controller, generation)) throw new DOMException("导入任务已取消", "AbortError");
  }

  onBeforeUnmount(cancelAndReset);

  return {
    importing,
    uploadingFile,
    uploadProgress,
    selectedFileName,
    task,
    flowError,
    refreshWarning,
    resultVisible,
    downloadingFailures,
    hasFeedback,
    progress,
    handleUpload,
    downloadFailures,
    openResult,
    cancelAndReset
  };
}
