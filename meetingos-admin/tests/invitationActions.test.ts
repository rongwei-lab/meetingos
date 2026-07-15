import { describe, expect, it } from "vitest";
import type { InvitationElementV1, InvitationQrPurpose, InvitationQueryTarget } from "../src/types/invitation";
import {
  INVITATION_QR_PURPOSE_OPTIONS,
  INVITATION_QUERY_TARGET_OPTIONS,
  extractInvitationShareTokenFragment,
  invitationQrDefaultLabel,
  invitationQrLabel,
  invitationQueryButtonDefaultText,
  isInvitationQueryTarget,
  normalizeInvitationQrPurpose,
  normalizeInvitationQueryTarget,
  resolveInvitationNavigationTarget,
  resolveInvitationQrTarget
} from "../src/utils/invitationActions";

const origin = "https://meeting.example";
const queryToken = "query-token+with spaces";
const shareToken = "share_token_1234567890";

describe("邀请函动作配置", () => {
  it("只接受白名单查询目标，并为历史非法值提供稳定的编辑器默认值", () => {
    const targets = INVITATION_QUERY_TARGET_OPTIONS.map((item) => item.value);

    expect(targets).toEqual([
      "QUERY_ENTRY",
      "COMBINED_QUERY",
      "SEAT_QUERY",
      "ROOM_QUERY",
      "DINNER_QUERY"
    ]);
    targets.forEach((target) => expect(isInvitationQueryTarget(target)).toBe(true));
    expect(isInvitationQueryTarget("https://evil.example")).toBe(false);
    expect(normalizeInvitationQueryTarget("SEAT_QUERY")).toBe("SEAT_QUERY");
    expect(normalizeInvitationQueryTarget("unknown")).toBe("COMBINED_QUERY");
  });

  it("二维码用途和默认说明覆盖报名、查询及历史邀请函", () => {
    const purposes = INVITATION_QR_PURPOSE_OPTIONS.map((item) => item.value);
    expect(purposes).toEqual([
      "REGISTRATION",
      "QUERY_ENTRY",
      "COMBINED_QUERY",
      "SEAT_QUERY",
      "ROOM_QUERY",
      "DINNER_QUERY",
      "CHANNEL_INVITATION"
    ]);
    expect(normalizeInvitationQrPurpose("ROOM_QUERY")).toBe("ROOM_QUERY");
    expect(normalizeInvitationQrPurpose("unknown")).toBe("CHANNEL_INVITATION");

    const expectedLabels: Record<InvitationQrPurpose, string> = {
      REGISTRATION: "扫码报名",
      QUERY_ENTRY: "扫码查询",
      COMBINED_QUERY: "扫码综合查询",
      SEAT_QUERY: "扫码查询座位",
      ROOM_QUERY: "扫码查询住宿",
      DINNER_QUERY: "扫码查询晚宴",
      CHANNEL_INVITATION: "扫码查看邀请函"
    };
    Object.entries(expectedLabels).forEach(([purpose, label]) => {
      expect(invitationQrDefaultLabel(purpose as InvitationQrPurpose)).toBe(label);
      expect(invitationQrLabel(qrElement({ purpose }))).toBe(label);
    });
  });

  it("保留旧二维码默认说明，并区分自定义说明和显式隐藏", () => {
    expect(invitationQrLabel(qrElement({}))).toBe("扫码报名");
    expect(invitationQrLabel(qrElement({ purpose: "REGISTRATION", label: "微信扫码报名" }))).toBe("微信扫码报名");
    expect(invitationQrLabel(qrElement({ purpose: "REGISTRATION", label: "" }))).toBe("");
  });

  it("为各查询按钮返回与目标一致的默认文字", () => {
    const expected: Record<InvitationQueryTarget, string> = {
      QUERY_ENTRY: "立即查询",
      COMBINED_QUERY: "立即查询",
      SEAT_QUERY: "查询座位",
      ROOM_QUERY: "查询住宿",
      DINNER_QUERY: "查询晚宴"
    };

    Object.entries(expected).forEach(([target, label]) => {
      expect(invitationQueryButtonDefaultText(target as InvitationQueryTarget)).toBe(label);
    });
  });
});

describe("邀请函动作地址", () => {
  it.each([
    ["QUERY_ENTRY", "/public/entry"],
    ["COMBINED_QUERY", "/public/query"],
    ["SEAT_QUERY", "/public/seat-query"],
    ["ROOM_QUERY", "/public/room-query"],
    ["DINNER_QUERY", "/public/dinner-query"],
    ["SELF_CHECKIN", "/public/checkin"]
  ] as const)("将 %s 解析为同源公开地址", (target, path) => {
    const result = resolveInvitationNavigationTarget(target, {
      origin,
      publicQueryToken: queryToken
    });

    expect(result.unavailableReason).toBe("");
    const url = new URL(result.url);
    expect(url.origin).toBe(origin);
    expect(url.pathname).toBe(path);
    expect(url.searchParams.get("token")).toBe(queryToken);
  });

  it("缺少公开查询令牌时不生成可枚举的会议地址", () => {
    expect(resolveInvitationNavigationTarget("SELF_CHECKIN", {
      origin,
      meetingCode: "MEETING001"
    })).toEqual({ url: "", unavailableReason: "会议未配置公开查询令牌" });
  });

  it("查询用途二维码复用同一白名单导航解析", () => {
    const result = resolveInvitationQrTarget(qrElement({ purpose: "DINNER_QUERY" }), {
      origin,
      publicQueryToken: queryToken
    });

    expect(result.unavailableReason).toBe("");
    expect(new URL(result.url).pathname).toBe("/public/dinner-query");
    expect(new URL(result.url).searchParams.get("token")).toBe(queryToken);
  });

  it("报名二维码进入报名中转并携带当前邀请渠道片段", () => {
    const result = resolveInvitationQrTarget(qrElement({ purpose: "REGISTRATION" }), {
      origin,
      meetingCode: "MEETING/001",
      shareToken
    });

    expect(result).toEqual({
      url: `${origin}/r/MEETING%2F001#mos_inv=${shareToken}`,
      unavailableReason: ""
    });
    expect(extractInvitationShareTokenFragment(new URL(result.url).hash)).toBe(shareToken);
  });

  it("报名二维码可从邀请地址提取渠道 token，但缺少任一必要标识时不可用", () => {
    const fromShareUrl = resolveInvitationQrTarget(qrElement({ purpose: "REGISTRATION" }), {
      origin,
      meetingCode: "MEETING001",
      shareUrl: `${origin}/i/${shareToken}`
    });
    expect(extractInvitationShareTokenFragment(new URL(fromShareUrl.url).hash)).toBe(shareToken);

    expect(resolveInvitationQrTarget(qrElement({ purpose: "REGISTRATION" }), {
      origin,
      shareToken
    }).unavailableReason).toBe("会议缺少报名编号");
    expect(resolveInvitationQrTarget(qrElement({ purpose: "REGISTRATION" }), {
      origin,
      meetingCode: "MEETING001"
    }).unavailableReason).toBe("请先创建并启用邀请渠道");
  });

  it("历史渠道二维码优先使用服务端 shareUrl，并可回退到 shareToken", () => {
    expect(resolveInvitationQrTarget(qrElement({}), {
      origin,
      shareUrl: "https://cdn.example/invitation/current"
    })).toEqual({ url: "https://cdn.example/invitation/current", unavailableReason: "" });

    expect(resolveInvitationQrTarget(qrElement({ purpose: "CHANNEL_INVITATION" }), {
      origin,
      shareToken
    })).toEqual({ url: `${origin}/i/${shareToken}`, unavailableReason: "" });
  });

  it("拒绝未知二维码用途，且只接受严格格式的归因片段", () => {
    expect(resolveInvitationQrTarget(qrElement({ purpose: "https://evil.example" }), {
      origin,
      shareUrl: `${origin}/i/${shareToken}`
    })).toEqual({ url: "", unavailableReason: "二维码用途不受支持" });

    expect(extractInvitationShareTokenFragment(`#mos_inv=${shareToken}`)).toBe(shareToken);
    expect(extractInvitationShareTokenFragment(`#mos_inv=${shareToken}&next=https://evil.example`)).toBe("");
    expect(extractInvitationShareTokenFragment("#mos_inv=short")).toBe("");
    expect(extractInvitationShareTokenFragment(`#other=${shareToken}`)).toBe("");
  });
});

function qrElement(props: InvitationElementV1["props"]): InvitationElementV1 {
  return {
    id: "qr",
    type: "CHANNEL_QR_CODE",
    x: 0,
    y: 0,
    width: 180,
    height: 205,
    rotation: 0,
    zIndex: 1,
    locked: false,
    props
  };
}
