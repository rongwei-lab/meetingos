import { computed, onMounted, watch } from "vue";
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

  async function syncMeetingFromRoute() {
    const meetingId = routeMeetingId.value;
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
      if (!meeting) {
        throw new Error("会议不存在或无权访问");
      }
      return meeting;
    } catch (error) {
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

  return {
    routeMeetingId,
    syncMeetingFromRoute
  };
}
