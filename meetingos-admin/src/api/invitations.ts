import { http, unwrapApi } from "@/api/http";
import type {
  InvitationAnalytics,
  InvitationAsset,
  InvitationDesignDocumentV1,
  InvitationDetail,
  InvitationEventReceipt,
  InvitationShareLink,
  InvitationSummary,
  PublicInvitationPayload,
  RegistrationIntent,
  TduckCapabilities,
  InvitationChannelType
} from "@/types/invitation";

const invitationBase = (meetingId: string | number) => `/admin/meetings/${encodeURIComponent(String(meetingId))}/invitations`;

export async function listInvitations(meetingId: string | number) {
  return unwrapApi(await http.get<InvitationSummary[]>(invitationBase(meetingId)));
}

export async function getInvitation(meetingId: string | number, invitationId: string | number) {
  return unwrapApi(await http.get<InvitationDetail>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}`));
}

export async function createInvitation(meetingId: string | number, payload: { name: string; document?: InvitationDesignDocumentV1 }) {
  return unwrapApi(await http.post<InvitationDetail>(invitationBase(meetingId), payload));
}

export async function copyInvitation(meetingId: string | number, invitationId: string | number, name?: string) {
  return unwrapApi(await http.post<InvitationDetail>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}/copy`, { name }));
}

export async function saveInvitationDraft(
  meetingId: string | number,
  invitationId: string | number,
  payload: { expectedRevision: number; name?: string; document: InvitationDesignDocumentV1 }
) {
  return unwrapApi(await http.put<InvitationDetail>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}/draft`, payload));
}

export async function changeInvitationStatus(meetingId: string | number, invitationId: string | number, action: "publish" | "unpublish" | "archive") {
  return unwrapApi(await http.post<InvitationDetail>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}/${action}`));
}

export async function listInvitationAssets(meetingId: string | number) {
  return unwrapApi(await http.get<InvitationAsset[]>(`${invitationBase(meetingId)}/assets`));
}

export async function uploadInvitationAsset(meetingId: string | number, file: File) {
  const data = new FormData();
  data.append("file", file);
  return unwrapApi(await http.post<InvitationAsset>(`${invitationBase(meetingId)}/assets`, data, { headers: { "Content-Type": "multipart/form-data" } }));
}

export async function listInvitationShareLinks(meetingId: string | number, invitationId: string | number) {
  return unwrapApi(await http.get<InvitationShareLink[]>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}/share-links`));
}

export async function createInvitationShareLink(meetingId: string | number, invitationId: string | number, payload: { channelType: InvitationChannelType; channelName: string; expiresAt?: string | null }) {
  return unwrapApi(await http.post<InvitationShareLink>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}/share-links`, payload));
}

export async function updateInvitationShareLink(
  meetingId: string | number,
  invitationId: string | number,
  linkId: string | number,
  payload: { channelType?: InvitationChannelType; channelName?: string; enabled?: boolean; expiresAt?: string | null; clearExpiresAt?: boolean }
) {
  return unwrapApi(await http.put<InvitationShareLink>(`${invitationBase(meetingId)}/${encodeURIComponent(String(invitationId))}/share-links/${encodeURIComponent(String(linkId))}`, payload));
}

export async function getInvitationAnalytics(meetingId: string | number, params: { invitationId?: number; from?: string; to?: string } = {}) {
  return unwrapApi(await http.get<InvitationAnalytics>(`${invitationBase(meetingId)}/analytics`, { params }));
}

export async function getPublicInvitation(shareToken: string) {
  return unwrapApi(await http.get<PublicInvitationPayload>(`/public/invitations/${encodeURIComponent(shareToken)}`));
}

export async function recordInvitationView(shareToken: string, clientEventId: string) {
  return unwrapApi(await http.post<InvitationEventReceipt>(`/public/invitations/${encodeURIComponent(shareToken)}/views`, { clientEventId }));
}

export async function createRegistrationIntent(shareToken: string, clientEventId: string) {
  return unwrapApi(await http.post<RegistrationIntent>(`/public/invitations/${encodeURIComponent(shareToken)}/registration-intents`, { clientEventId }));
}

export async function detectTduckAttributionCapability() {
  const response = await fetch("/tduck/meetingos-capabilities.json", { credentials: "same-origin", cache: "no-store" });
  if (!response.ok) return false;
  const payload = await response.json() as TduckCapabilities;
  return payload.capabilities?.meetingosAttributionExtValue === "v1";
}
