import { describe, expect, it } from "vitest";
import {
  INVITATION_SHAPE_OPTIONS,
  invitationShapePoints,
  isSupportedInvitationShape,
  normalizeInvitationShape
} from "../src/utils/invitationShape";
import { createBlankInvitationDocument, validateInvitationDocument } from "../src/utils/invitationDocument";

describe("邀请函形状几何", () => {
  it("提供七种规范形状并兼容旧 circle", () => {
    expect(INVITATION_SHAPE_OPTIONS.map((item) => item.value)).toEqual([
      "rectangle", "rounded-rectangle", "ellipse", "triangle", "diamond", "hexagon", "star"
    ]);
    expect(normalizeInvitationShape("circle")).toBe("ellipse");
    expect(normalizeInvitationShape("unknown")).toBe("rectangle");
    expect(isSupportedInvitationShape("circle")).toBe(true);
    expect(isSupportedInvitationShape("pentagon")).toBe(false);
  });

  it("多边形点集覆盖完整边界并按半线宽内缩", () => {
    const points = invitationShapePoints("star", 100, 80, 2);
    expect(points).toHaveLength(10);
    expect(Math.min(...points.map((point) => point.x))).toBeCloseTo(2);
    expect(Math.max(...points.map((point) => point.x))).toBeCloseTo(98);
    expect(Math.min(...points.map((point) => point.y))).toBeCloseTo(2);
    expect(Math.max(...points.map((point) => point.y))).toBeCloseTo(78);
  });

  it("拒绝会产生 NaN 或越界几何的形状数值", () => {
    const document = createBlankInvitationDocument();
    document.h5.elements.push({
      id: "invalid_shape",
      type: "SHAPE",
      x: 0,
      y: 0,
      width: 100,
      height: 80,
      rotation: 0,
      zIndex: 1,
      locked: false,
      props: { shape: "star", strokeWidth: "wide", borderRadius: 50, opacity: 2 }
    });

    expect(validateInvitationDocument(document, true)).toEqual(expect.arrayContaining([
      "元素透明度不合法：invalid_shape",
      "形状描边宽度不合法：invalid_shape",
      "形状圆角半径不合法：invalid_shape"
    ]));
  });
});
