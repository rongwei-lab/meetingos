export type InvitationStatus = "DRAFT" | "PUBLISHED" | "UNPUBLISHED" | "ARCHIVED";

export type InvitationChannelType =
  | "WECHAT_FRIENDS"
  | "WECHAT_MOMENTS"
  | "EMAIL"
  | "OFFLINE_POSTER"
  | "OTHER";

export type InvitationElementType =
  | "TEXT"
  | "IMAGE"
  | "SHAPE"
  | "DIVIDER"
  | "MEETING_FIELD"
  | "REGISTRATION_BUTTON"
  | "CHECKIN_BUTTON"
  | "QUERY_BUTTON"
  | "CHANNEL_QR_CODE"
  | "COUNTDOWN"
  | "BACKGROUND_MUSIC"
  | "VIDEO"
  | "CAROUSEL"
  | "AMAP";

export type InvitationQueryTarget =
  | "QUERY_ENTRY"
  | "COMBINED_QUERY"
  | "SEAT_QUERY"
  | "ROOM_QUERY"
  | "DINNER_QUERY";

export type InvitationQrPurpose =
  | "REGISTRATION"
  | InvitationQueryTarget
  | "CHANNEL_INVITATION";

export type InvitationNavigationTarget = InvitationQueryTarget | "SELF_CHECKIN";

export type InvitationMeetingField =
  | "NAME"
  | "ORGANIZER"
  | "CO_ORGANIZER"
  | "START_TIME"
  | "END_TIME"
  | "LOCATION"
  | "VENUE_NAME"
  | "DESCRIPTION"
  | "AGENDA"
  | "COVER_IMAGE"
  | "CONTACT_NAME"
  | "CONTACT_PHONE";

export type InvitationElementProps = Record<string, string | number | boolean | number[] | null | undefined>;

export interface InvitationElementV1 {
  id: string;
  type: InvitationElementType;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  zIndex: number;
  locked: boolean;
  props: InvitationElementProps;
}

export interface InvitationCanvasV1 {
  width: number;
  height: number;
  backgroundColor: string;
  backgroundImageAssetId: number | null;
  elements: InvitationElementV1[];
}

export interface InvitationDesignDocumentV1 {
  version: 1;
  h5: InvitationCanvasV1;
  poster: InvitationCanvasV1;
}

export interface InvitationSummary {
  id: number;
  name: string;
  status: InvitationStatus;
  draftRevision: number;
  latestVersionNo: number | null;
  shareLinkCount: number;
  publishedAt: string | null;
  updatedAt: string;
}

export interface InvitationVersion {
  id?: number;
  versionNo: number;
  designDocument?: InvitationDesignDocumentV1;
  document?: InvitationDesignDocumentV1;
  publishedAt?: string;
  meetingSnapshot?: InvitationMeetingSnapshot;
}

export interface InvitationDetail {
  id: number;
  name: string;
  status: InvitationStatus;
  draftRevision: number;
  publishedAt: string | null;
  updatedAt: string;
  draftDocument: InvitationDesignDocumentV1;
  latestPublishedVersion: InvitationVersion | null;
}

export interface InvitationAsset {
  id: number;
  originalName: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  width: number | null;
  height: number | null;
  createdAt: string;
}

export interface InvitationShareLink {
  id: number;
  invitationId: number;
  channelType: InvitationChannelType;
  channelName: string;
  shareToken: string;
  shareUrl: string;
  enabled: boolean;
  expiresAt: string | null;
  currentVersionNo: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface InvitationMeetingSnapshot {
  id: number;
  code: string;
  name: string;
  organizer: string;
  coOrganizer?: string | null;
  startTime: string;
  endTime: string;
  registrationStartTime?: string | null;
  registrationEndTime?: string | null;
  location?: string | null;
  venueName?: string | null;
  description?: string | null;
  agenda?: string | null;
  coverImageUrl?: string | null;
  contactName?: string | null;
  contactPhone?: string | null;
}

export interface PublicInvitationPayload {
  invitationId: number;
  versionNo: number;
  channelType: InvitationChannelType;
  channelName: string;
  designDocument: InvitationDesignDocumentV1;
  meeting: InvitationMeetingSnapshot;
  publicQueryToken?: string | null;
  assets: InvitationAsset[];
  registrationOpen: boolean;
  registrationMessage: string;
}

export interface InvitationAnalyticsDaily {
  date: string;
  pageViews: number;
  uniqueVisitors: number;
  ctaClicks: number;
  attributedRegistrations: number;
}

export interface InvitationAnalyticsChannel {
  shareLinkId: number;
  invitationId: number;
  invitationName: string;
  channelType: InvitationChannelType;
  channelName: string;
  pageViews: number;
  uniqueVisitors: number;
  ctaClicks: number;
  attributedRegistrations: number;
  activeAttributedRegistrations: number;
}

export interface InvitationAnalytics {
  pageViews: number;
  uniqueVisitors: number;
  ctaClicks: number;
  uniqueClickVisitors: number;
  attributedRegistrations: number;
  activeAttributedRegistrations: number;
  directUnattributedRegistrations: number;
  invalidTokenRegistrations: number;
  legacyUnattributedRegistrations: number;
  currentActiveRegistrations: number;
  clickThroughRate: number;
  registrationConversionRate: number;
  daily: InvitationAnalyticsDaily[];
  channels: InvitationAnalyticsChannel[];
}

export interface RegistrationIntent {
  attributionToken: string;
  redirectUrl: string;
  expiresAt: string;
}

export interface InvitationEventReceipt {
  recorded: boolean;
}

export interface TduckCapabilities {
  capabilities?: {
    meetingosAttributionExtValue?: string;
  };
}
