export type InvitationShapeKind =
  | "rectangle"
  | "rounded-rectangle"
  | "ellipse"
  | "triangle"
  | "diamond"
  | "hexagon"
  | "star";

export interface InvitationShapePoint {
  x: number;
  y: number;
}

export const INVITATION_SHAPE_OPTIONS: Array<{ label: string; value: InvitationShapeKind }> = [
  { label: "矩形", value: "rectangle" },
  { label: "圆角矩形", value: "rounded-rectangle" },
  { label: "圆形 / 椭圆", value: "ellipse" },
  { label: "三角形", value: "triangle" },
  { label: "菱形", value: "diamond" },
  { label: "六边形", value: "hexagon" },
  { label: "五角星", value: "star" }
];

const SHAPE_KINDS = new Set<InvitationShapeKind>(INVITATION_SHAPE_OPTIONS.map((item) => item.value));
const POLYGON_UNIT_POINTS: Partial<Record<InvitationShapeKind, InvitationShapePoint[]>> = {
  triangle: [{ x: 0.5, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }],
  diamond: [{ x: 0.5, y: 0 }, { x: 1, y: 0.5 }, { x: 0.5, y: 1 }, { x: 0, y: 0.5 }],
  hexagon: [{ x: 0.25, y: 0 }, { x: 0.75, y: 0 }, { x: 1, y: 0.5 }, { x: 0.75, y: 1 }, { x: 0.25, y: 1 }, { x: 0, y: 0.5 }],
  star: normalizeUnitPoints(Array.from({ length: 10 }, (_, index) => {
    const angle = -Math.PI / 2 + index * Math.PI / 5;
    const radius = index % 2 === 0 ? 0.5 : 0.22;
    return { x: 0.5 + Math.cos(angle) * radius, y: 0.5 + Math.sin(angle) * radius };
  }))
};

export function normalizeInvitationShape(value: unknown): InvitationShapeKind {
  // 旧版本的 circle 在非正方形尺寸下实际渲染为椭圆，继续按原视觉兼容。
  if (value === "circle") return "ellipse";
  return typeof value === "string" && SHAPE_KINDS.has(value as InvitationShapeKind)
    ? value as InvitationShapeKind
    : "rectangle";
}

export function isSupportedInvitationShape(value: unknown) {
  return value == null || value === "circle" || (typeof value === "string" && SHAPE_KINDS.has(value as InvitationShapeKind));
}

export function isInvitationPolygonShape(shape: InvitationShapeKind) {
  return Boolean(POLYGON_UNIT_POINTS[shape]);
}

export function invitationShapePoints(shape: InvitationShapeKind, width: number, height: number, inset = 0) {
  const unitPoints = POLYGON_UNIT_POINTS[shape] || [];
  const contentWidth = Math.max(0, width - inset * 2);
  const contentHeight = Math.max(0, height - inset * 2);
  return unitPoints.map((point) => ({
    x: inset + point.x * contentWidth,
    y: inset + point.y * contentHeight
  }));
}

export function invitationShapeRadius(shape: InvitationShapeKind, width: number, height: number, borderRadius: unknown) {
  const maximum = Math.max(0, Math.min(width, height) / 2);
  const configured = Math.max(0, Number(borderRadius || 0));
  const radius = shape === "rounded-rectangle" ? Math.max(configured, Math.min(width, height) * 0.18) : configured;
  return Math.min(maximum, radius);
}

function normalizeUnitPoints(points: InvitationShapePoint[]) {
  const xs = points.map((point) => point.x);
  const ys = points.map((point) => point.y);
  const minimumX = Math.min(...xs);
  const maximumX = Math.max(...xs);
  const minimumY = Math.min(...ys);
  const maximumY = Math.max(...ys);
  return points.map((point) => ({
    x: (point.x - minimumX) / Math.max(Number.EPSILON, maximumX - minimumX),
    y: (point.y - minimumY) / Math.max(Number.EPSILON, maximumY - minimumY)
  }));
}
