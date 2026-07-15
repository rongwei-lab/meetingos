import { describe, expect, it } from "vitest";
import type { InvitationShareLink, InvitationStatus } from "../src/types/invitation";
import {
  invitationShareLinkAvailability,
  normalizeInvitationLocalDateTime
} from "../src/utils/invitationShareLink";

const localNow = new Date(2026, 6, 14, 10, 0, 0).getTime();

function link(overrides: Partial<InvitationShareLink> = {}): InvitationShareLink {
  return {
    id: 11,
    invitationId: 7,
    channelType: "WECHAT_FRIENDS",
    channelName: "社群",
    shareToken: "token",
    shareUrl: "https://meeting.example/i/token",
    enabled: true,
    expiresAt: "2026-07-14T10:01:00",
    currentVersionNo: 2,
    createdAt: "2026-07-01T09:00:00",
    updatedAt: "2026-07-01T09:00:00",
    ...overrides
  };
}

function invitation(status: InvitationStatus = "PUBLISHED") {
  return [{ id: 7, status }];
}

describe("邀请渠道可用状态", () => {
  it("同时要求方案发布、渠道启用、存在发布版本且尚未过期", () => {
    expect(invitationShareLinkAvailability(link(), invitation(), localNow)).toEqual({ available: true, reason: null });
    expect(invitationShareLinkAvailability(link(), invitation("DRAFT"), localNow).reason).toBe("方案未发布");
    expect(invitationShareLinkAvailability(link({ enabled: false }), invitation(), localNow).reason).toBe("渠道已停用");
    expect(invitationShareLinkAvailability(link({ currentVersionNo: null }), invitation(), localNow).reason).toBe("尚无发布版本");
    expect(invitationShareLinkAvailability(link({ expiresAt: "2026-07-14T10:00:00" }), invitation(), localNow).reason).toBe("渠道已过期");
  });

  it("后端返回异常时间或找不到所属方案时保守判为不可用", () => {
    expect(invitationShareLinkAvailability(link({ expiresAt: "2026-02-30T10:00:00" }), invitation(), localNow).reason).toBe("失效时间无效");
    expect(invitationShareLinkAvailability(link(), [], localNow).reason).toBe("方案状态未知");
  });
});

describe("邀请渠道 LocalDateTime 契约", () => {
  it("原样保留用户选择的本地时间，不转换成 UTC", () => {
    expect(normalizeInvitationLocalDateTime("2026-07-14T20:30:00")).toBe("2026-07-14T20:30:00");
    expect(normalizeInvitationLocalDateTime(null)).toBeNull();
  });

  it("拒绝带时区或不存在的本地时间", () => {
    expect(() => normalizeInvitationLocalDateTime("2026-07-14T12:30:00.000Z")).toThrow("失效时间格式无效");
    expect(() => normalizeInvitationLocalDateTime("2026-02-30T12:30:00")).toThrow("失效时间格式无效");
  });
});
