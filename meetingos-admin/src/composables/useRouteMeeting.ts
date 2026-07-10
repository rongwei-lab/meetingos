import { computed, onBeforeUnmount, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useMessage } from "naive-ui";
import { useMeetingStore } from "@/stores/meeting";

function firstRouteParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

export function useRouteMeeting(options: { autoSync?: boolean; loadWorkspace?: boolean; redirectOnMissing?: boolean } = {}) {
  const route = useRoute();
  const router = useRouter();
  const message = useMessage();
  const store = useMeetingStore();
  const routeMeetingId = computed(() => firstRouteParam(route.params.meetingId) || firstRouteParam(route.params.id) || "");
  let syncGeneration = 0;

  async function syncMeetingFromRoute() {
    const meetingId = routeMeetingId.value;
    const generation = ++syncGeneration;
    if (!meetingId) {
      if (options.redirectOnMissing !== false) {
        message.warning("请先选择会议");
        await router.replace({ name: "meetings" });
      }
      return null;
    }

    try {
      // URL 是会议上下文的权威来源。进入或刷新业务页时先同步当前会议，
      // 再由页面使用 store.current* getter 读取该会议的数据。
      const meeting = await store.selectMeeting(meetingId, { loadWorkspace: options.loadWorkspace });
      if (generation !== syncGeneration || routeMeetingId.value !== meetingId) return null;
      if (!meeting) {
        throw new Error("会议不存在或无权访问");
      }
      return meeting;
    } catch (error) {
      // 快速切换会议时，旧请求失败不应把已经进入的新会议重定向回列表。
      if (generation !== syncGeneration || routeMeetingId.value !== meetingId) return null;
      message.error(error instanceof Error ? error.message : "会议不存在或无权访问");
      await router.replace({ name: "meetings" });
      return null;
    }
  }

  if (options.autoSync !== false) {
    onMounted(syncMeetingFromRoute);

    watch(routeMeetingId, (meetingId, previousMeetingId) => {
      if (meetingId && meetingId !== previousMeetingId) {
        void syncMeetingFromRoute();
      }
    });
  }

  onBeforeUnmount(() => {
    syncGeneration += 1;
  });

  return {
    routeMeetingId,
    syncMeetingFromRoute
  };
}
