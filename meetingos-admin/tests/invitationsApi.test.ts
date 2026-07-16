import { beforeEach, describe, expect, it, vi } from "vitest";
import { saveInvitationDraft } from "../src/api/invitations";
import type { InvitationDesignDocumentV1 } from "../src/types/invitation";

const state = vi.hoisted(() => ({
  put: vi.fn()
}));

vi.mock("@/api/http", () => ({
  http: { put: state.put },
  unwrapApi: <T>(response: { data: T }) => response.data
}));

const payload = {
  expectedRevision: 0,
  document: {} as InvitationDesignDocumentV1
};

describe("邀请函草稿保存请求", () => {
  beforeEach(() => {
    state.put.mockReset().mockResolvedValue({ data: { id: 2 } });
  });

  it("自动和手动草稿保存共用同一审计请求", async () => {
    await saveInvitationDraft(1, 2, payload);

    expect(state.put).toHaveBeenCalledWith("/admin/meetings/1/invitations/2/draft", payload);
  });
});
