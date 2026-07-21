import { describe, expect, it } from "vitest";
import { operationActionLabel, operationTargetLabel } from "@/utils/meetingDisplay";

describe("operation log display labels", () => {
  it("renders labels for newly audited operations", () => {
    expect(operationActionLabel("AUTH_LOGIN")).toBe("登录");
    expect(operationActionLabel("AUTH_LOGOUT")).toBe("退出登录");
    expect(operationActionLabel("PUBLIC_SELF_CHECKIN")).toBe("自助签到");
    expect(operationActionLabel("TDUCK_WEBHOOK_SYNC")).toBe("TDuck Webhook 同步");
    expect(operationActionLabel("TDUCK_DUPLICATE_CONFLICT_RESOLVE")).toBe("处理 TDuck 重复报名");
    expect(operationTargetLabel("AUTH_SESSION")).toBe("登录会话");
    expect(operationTargetLabel("TDUCK_SYNC", "71")).toBe("TDuck 同步：71");
  });

  it("renders labels for invitation audit operations", () => {
    expect(operationActionLabel("INVITATION_CREATE")).toBe("创建邀请函");
    expect(operationActionLabel("INVITATION_DRAFT_SAVE")).toBe("保存邀请函草稿");
    expect(operationActionLabel("INVITATION_SHARE_LINK_UPDATE")).toBe("更新邀请函渠道链接");
    expect(operationActionLabel("INVITATION_ASSET_UPLOAD")).toBe("上传邀请函素材");
    expect(operationTargetLabel("MEETING_INVITATION", "18")).toBe("邀请函：18");
    expect(operationTargetLabel("INVITATION_SHARE_LINK", "23")).toBe("邀请函渠道链接：23");
    expect(operationTargetLabel("INVITATION_ASSET", "35")).toBe("邀请函素材：35");
  });
});
