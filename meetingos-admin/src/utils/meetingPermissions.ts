export interface ExistingMeetingPermission {
  id?: string | number;
  meetingId?: string | number;
  userId?: string | number;
  role?: string;
  permission?: string;
}

export type MeetingAuthorizationChange =
  | { type: "grant"; meetingId: string; role: string }
  | { type: "revoke"; meetingId: string; permissionId: string | number };

export function hasGlobalMeetingAccess(roles?: readonly string[] | null): boolean {
  return roles?.includes("SUPER_ADMIN") ?? false;
}

export function planMeetingAuthorizationChanges(
  userId: string | number,
  managedMeetingIds: string[],
  authorizedMeetingIds: string[],
  existingPermissions: ExistingMeetingPermission[],
  roles: readonly string[] = []
): MeetingAuthorizationChange[] {
  if (hasGlobalMeetingAccess(roles)) {
    return [];
  }

  const authorizedMeetingIdSet = new Set(authorizedMeetingIds);
  const existingByMeetingId = new Map(
    existingPermissions
      .filter((permission) => String(permission.userId) === String(userId) && permission.meetingId != null)
      .map((permission) => [String(permission.meetingId), permission])
  );

  return managedMeetingIds.flatMap<MeetingAuthorizationChange>((meetingId) => {
    const existing = existingByMeetingId.get(meetingId);
    const existingRole = existing?.role || existing?.permission || null;
    if (authorizedMeetingIdSet.has(meetingId) && existingRole !== "MANAGER") {
      /*
       * 会议记录只承担“用户可访问该会议”的绑定作用，统一保存为 MANAGER。
       * 实际可执行的管理、签到、住宿或只读能力仍由用户的全局角色限制。
       */
      return [{ type: "grant", meetingId, role: "MANAGER" }];
    }
    if (!authorizedMeetingIdSet.has(meetingId) && existing?.id != null) {
      return [{ type: "revoke", meetingId, permissionId: existing.id }];
    }
    return [];
  });
}
