import { describe, expect, it } from "vitest";
import { appendMeetingAttributionFragment, readMeetingAttributionToken } from "../src/utils/invitationAttribution";

describe("邀请报名归因 fragment", () => {
  it("只接受完整且 URL-safe 的 mos_at 令牌", () => {
    const token = "1.click_ABC.meeting-7.fingerprint~v1.1784000000.signature_XYZ";
    expect(readMeetingAttributionToken(`#mos_at=${token}`)).toBe(token);
    expect(readMeetingAttributionToken("#foo=1&mos_at=abc")).toBe("");
    expect(readMeetingAttributionToken("#mos_at=abc%2Edef")).toBe("");
    expect(readMeetingAttributionToken("#mos_at=")).toBe("");
    expect(readMeetingAttributionToken(`#mos_at=${"a".repeat(256)}`)).toBe("");
  });

  it("清理 TDuck 原 fragment 后附加合法归因令牌", () => {
    const token = "1.click_ABC.meeting-7.fingerprint~v1.1784000000.signature_XYZ";
    expect(appendMeetingAttributionFragment("https://form.example.com/s/meeting#legacy", `#mos_at=${token}`))
      .toBe(`https://form.example.com/s/meeting#mos_at=${token}`);
  });

  it("无效 fragment 不会透传且原 fragment 也会被移除", () => {
    expect(appendMeetingAttributionFragment("https://form.example.com/s/meeting#legacy", "#mos_at=bad%2Evalue"))
      .toBe("https://form.example.com/s/meeting");
  });
});
