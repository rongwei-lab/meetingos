import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { useMeetingStore } from "@/stores/meeting";

function redirectToCurrentMeeting(suffix: string) {
  return () => {
    const meetingStore = useMeetingStore();
    return meetingStore.currentMeetingId ? `/meetings/${meetingStore.currentMeetingId}/${suffix}` : "/meetings";
  };
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: () => import("@/views/auth/LoginView.vue") },
    {
      path: "/public",
      component: () => import("@/layouts/PublicLayout.vue"),
      children: [
        { path: "entry", name: "public-entry", component: () => import("@/views/public/PublicEntryView.vue") },
        { path: "query", name: "combined-query", component: () => import("@/views/public/CombinedQueryView.vue") },
        { path: "seat-query", name: "seat-query", component: () => import("@/views/public/SeatQueryView.vue") },
        { path: "room-query", name: "room-query", component: () => import("@/views/public/RoomQueryView.vue") },
        { path: "dinner-query", name: "dinner-query", component: () => import("@/views/public/DinnerQueryView.vue") },
        { path: "checkin", name: "public-checkin", component: () => import("@/views/public/SelfCheckinView.vue") },
        { path: "checkin-code", name: "checkin-code", component: () => import("@/views/public/CheckinCodeView.vue") }
      ]
    },
    {
      path: "/r/:meetingCode",
      name: "registration-redirect",
      component: () => import("@/views/public/RegistrationRedirectView.vue")
    },
    {
      path: "/",
      component: () => import("@/layouts/AdminLayout.vue"),
      meta: { requiresAuth: true },
      children: [
        { path: "", redirect: "/meetings" },
        { path: "meetings", name: "meetings", component: () => import("@/views/meeting/MeetingListView.vue") },
        { path: "meetings/:meetingId", redirect: (to) => `/meetings/${String(to.params.meetingId)}/workspace` },
        { path: "meetings/:meetingId/checkin", redirect: (to) => `/meetings/${String(to.params.meetingId)}/workspace` },
        {
          path: "meetings/:meetingId/workspace",
          name: "meeting-workspace",
          meta: { requiresMeeting: true },
          component: () => import("@/views/dashboard/DashboardView.vue")
        },
        {
          path: "meetings/:meetingId/detail",
          name: "meeting-detail",
          meta: { requiresMeeting: true },
          component: () => import("@/views/meeting/MeetingDetailView.vue")
        },
        {
          path: "meetings/:meetingId/registration",
          name: "meeting-registration",
          meta: { requiresMeeting: true },
          component: () => import("@/views/registration/RegistrationConfigView.vue")
        },
        {
          path: "meetings/:meetingId/attendees",
          name: "meeting-attendees",
          meta: { requiresMeeting: true },
          component: () => import("@/views/attendee/AttendeeView.vue")
        },
        {
          path: "meetings/:meetingId/seating",
          name: "meeting-seating",
          meta: { requiresMeeting: true },
          component: () => import("@/views/seating/SeatingView.vue")
        },
        {
          path: "meetings/:meetingId/rooms",
          name: "meeting-rooms",
          meta: { requiresMeeting: true },
          component: () => import("@/views/room/RoomView.vue")
        },
        {
          path: "meetings/:meetingId/dinner",
          name: "meeting-dinner",
          meta: { requiresMeeting: true },
          component: () => import("@/views/dinner/DinnerView.vue")
        },
        {
          path: "meetings/:meetingId/statistics",
          name: "meeting-statistics",
          meta: { requiresMeeting: true },
          component: () => import("@/views/statistics/StatisticsView.vue")
        },
        {
          path: "meetings/:meetingId/exports",
          name: "meeting-exports",
          meta: { requiresMeeting: true },
          component: () => import("@/views/export/ExportView.vue")
        },
        {
          path: "meetings/:meetingId/logs",
          name: "meeting-logs",
          meta: { requiresMeeting: true, requiresSystemAdmin: true },
          component: () => import("@/views/system/OperationLogView.vue")
        },
        {
          path: "settings",
          name: "settings",
          meta: { requiresSystemAdmin: true },
          component: () => import("@/views/system/SettingsView.vue")
        },
        { path: "dashboard", redirect: redirectToCurrentMeeting("workspace") },
        { path: "registration", redirect: redirectToCurrentMeeting("registration") },
        { path: "attendees", redirect: redirectToCurrentMeeting("attendees") },
        { path: "seating", redirect: redirectToCurrentMeeting("seating") },
        { path: "rooms", redirect: redirectToCurrentMeeting("rooms") },
        { path: "dinner", redirect: redirectToCurrentMeeting("dinner") },
        { path: "statistics", redirect: redirectToCurrentMeeting("statistics") },
        { path: "exports", redirect: redirectToCurrentMeeting("exports") },
        { path: "logs", redirect: redirectToCurrentMeeting("logs") }
      ]
    }
  ]
});

router.beforeEach(async (to, from) => {
  const auth = useAuthStore();
  const needsSession = Boolean(to.meta.requiresAuth || to.meta.requiresSystemAdmin || to.name === "login");
  const hasSession = needsSession ? await auth.ensureSession() : auth.isAuthenticated;
  if (to.meta.requiresAuth && !hasSession) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresSystemAdmin && !auth.canAccessSystemSettings) {
    if (to.params.meetingId) {
      return { name: "meeting-workspace", params: { meetingId: to.params.meetingId } };
    }
    return { name: "meetings" };
  }
  if (to.name === "login" && hasSession) {
    return { name: "meetings" };
  }
  return true;
});

export default router;
