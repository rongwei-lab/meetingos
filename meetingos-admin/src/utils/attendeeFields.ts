import type { SelectGroupOption, SelectOption } from "naive-ui";

export type AttendeeFieldOption = SelectOption & {
  label: string;
  value: string;
};

export type AttendeeFieldGroupOption = SelectGroupOption & {
  type: "group";
  label: string;
  key: string;
  children: AttendeeFieldOption[];
};

export const attendeeFieldOptionGroups: AttendeeFieldGroupOption[] = [
  {
    type: "group",
    label: "基础身份",
    key: "basic",
    children: [
      { label: "姓名", value: "NAME" },
      { label: "手机号", value: "PHONE" },
      { label: "性别", value: "GENDER" },
      { label: "证件号", value: "IDENTITY_NO" },
      { label: "邮箱", value: "EMAIL" },
      { label: "参会类型", value: "ATTENDEE_TYPE" }
    ]
  },
  {
    type: "group",
    label: "单位与地域",
    key: "organization",
    children: [
      { label: "单位", value: "ORGANIZATION" },
      { label: "单位类型", value: "ORGANIZATION_TYPE" },
      { label: "部门", value: "DEPARTMENT" },
      { label: "职务", value: "TITLE" },
      { label: "行业", value: "INDUSTRY" },
      { label: "国家/地区", value: "COUNTRY_REGION" },
      { label: "省份", value: "PROVINCE" },
      { label: "城市", value: "CITY" },
      { label: "地址", value: "ADDRESS" }
    ]
  },
  {
    type: "group",
    label: "住宿与行程",
    key: "travel",
    children: [
      { label: "是否住宿", value: "ACCOMMODATION_REQUIRED" },
      { label: "房间号", value: "ROOM_NO" },
      { label: "房间备注", value: "ROOM_REMARK" },
      { label: "餐饮需求", value: "DIETARY_REQUIREMENT" },
      { label: "抵达时间", value: "ARRIVAL_DATE" },
      { label: "抵达交通", value: "ARRIVAL_TRANSPORT" },
      { label: "抵达航班/车次", value: "ARRIVAL_FLIGHT_TRAIN" },
      { label: "返程时间", value: "DEPARTURE_DATE" },
      { label: "返程交通", value: "DEPARTURE_TRANSPORT" },
      { label: "返程航班/车次", value: "DEPARTURE_FLIGHT_TRAIN" }
    ]
  },
  {
    type: "group",
    label: "业务扩展",
    key: "business",
    children: [
      { label: "紧急联系人", value: "EMERGENCY_CONTACT" },
      { label: "紧急联系电话", value: "EMERGENCY_PHONE" },
      { label: "随行人数", value: "COMPANION_COUNT" },
      { label: "发票抬头", value: "INVOICE_TITLE" },
      { label: "税号", value: "TAX_NO" },
      { label: "发票邮箱", value: "INVOICE_EMAIL" },
      { label: "缴费状态", value: "PAYMENT_STATUS" },
      { label: "报名来源", value: "SIGNUP_SOURCE" },
      { label: "邀请人", value: "INVITER" },
      { label: "肖像授权", value: "CONSENT_PHOTO" },
      { label: "隐私同意", value: "CONSENT_PRIVACY" },
      { label: "备注", value: "REMARK" }
    ]
  }
];

export const attendeeFieldOptions: SelectGroupOption[] = attendeeFieldOptionGroups;

const attendeeFieldLabelMap = Object.fromEntries(
  attendeeFieldOptionGroups.flatMap((group) => group.children.map((option) => [option.value, option.label]))
) as Record<string, string>;

export function attendeeFieldLabel(value: string) {
  return attendeeFieldLabelMap[value] || value;
}

export function inferAttendeeField(key: string, label = "") {
  const text = normalizeFieldText(`${key} ${label}`);
  /*
   * 先匹配更具体的扩展字段，再匹配手机号、邮箱、单位等通用字段，避免
   * “紧急电话”“发票邮箱”“单位类型”被误归到核心字段，影响后续入库和展示。
   */
  if (containsAny(text, "emergencycontact", "紧急联系人", "应急联系人")) return "EMERGENCY_CONTACT";
  if (containsAny(text, "emergencyphone", "emergencymobile", "紧急联系电话", "紧急电话", "应急电话", "应急手机号")) return "EMERGENCY_PHONE";
  if (containsAny(text, "invoiceemail", "billingemail", "发票邮箱", "收票邮箱")) return "INVOICE_EMAIL";
  if (containsAny(text, "taxno", "taxnumber", "taxid", "纳税人识别号", "税号")) return "TAX_NO";
  if (containsAny(text, "invoicetitle", "billingtitle", "发票抬头", "开票抬头")) return "INVOICE_TITLE";
  if (containsAny(text, "phone", "mobile", "tel", "手机号", "手机", "电话", "联系方式")) return "PHONE";
  if (containsAny(text, "name", "姓名", "参会人", "联系人")) return "NAME";
  if (containsAny(text, "organizationtype", "companytype", "unittype", "单位类型", "公司类型", "机构类型")) return "ORGANIZATION_TYPE";
  if (containsAny(text, "organization", "company", "corp", "单位", "公司", "机构")) return "ORGANIZATION";
  if (containsAny(text, "title", "position", "job", "职务", "职位", "岗位")) return "TITLE";
  if (containsAny(text, "email", "mail", "邮箱")) return "EMAIL";
  if (containsAny(text, "identity", "idcard", "idnumber", "证件", "身份证")) return "IDENTITY_NO";
  if (containsAny(text, "attendeetype", "guesttype", "参会类型", "嘉宾类型", "人员类型")) return "ATTENDEE_TYPE";
  if (containsAny(text, "gender", "sex", "性别")) return "GENDER";
  if (containsAny(text, "countryregion", "country", "region", "国家地区", "国家", "地区")) return "COUNTRY_REGION";
  if (containsAny(text, "province", "state", "省份", "省市", "省")) return "PROVINCE";
  if (containsAny(text, "city", "城市", "市")) return "CITY";
  if (containsAny(text, "industry", "sector", "行业", "产业")) return "INDUSTRY";
  if (containsAny(text, "department", "dept", "部门", "科室")) return "DEPARTMENT";
  if (containsAny(text, "address", "addr", "通讯地址", "联系地址", "地址")) return "ADDRESS";
  if (containsAny(text, "dietary", "meal", "food", "allergy", "halal", "vegetarian", "餐饮", "用餐", "忌口", "清真", "素食", "过敏")) return "DIETARY_REQUIREMENT";
  if (containsAny(text, "departureflight", "departuretrain", "departureno", "leaveno", "returnflight", "returntrain", "离开航班", "离开车次", "返程航班", "返程车次")) return "DEPARTURE_FLIGHT_TRAIN";
  if (containsAny(text, "departuretransport", "leavetransport", "returntransport", "departuretraffic", "返程交通", "离开交通", "离店交通")) return "DEPARTURE_TRANSPORT";
  if (containsAny(text, "departuredate", "departuretime", "leavedate", "leavetime", "returndate", "returntime", "离开时间", "离开日期", "返程时间", "返程日期")) return "DEPARTURE_DATE";
  if (containsAny(text, "arrivalflight", "arrivaltrain", "arriveno", "arrivetrip", "抵达航班", "到达航班", "抵达车次", "到达车次", "来程航班", "来程车次")) return "ARRIVAL_FLIGHT_TRAIN";
  if (containsAny(text, "arrivaltransport", "arrivetransport", "arrivaltraffic", "arrivetraffic", "抵达交通", "到达交通", "来程交通", "交通方式")) return "ARRIVAL_TRANSPORT";
  if (containsAny(text, "arrivaldate", "arrivaltime", "arrivedate", "抵达时间", "到达时间", "抵达日期", "到达日期", "来程时间")) return "ARRIVAL_DATE";
  if (containsAny(text, "companioncount", "guestcount", "accompanycount", "随行人数", "陪同人数", "同伴人数")) return "COMPANION_COUNT";
  if (containsAny(text, "paymentstatus", "paystatus", "paidstatus", "缴费状态", "支付状态", "付款状态")) return "PAYMENT_STATUS";
  if (containsAny(text, "signupsource", "registrationsource", "source", "channel", "报名来源", "报名渠道", "来源渠道", "渠道")) return "SIGNUP_SOURCE";
  if (containsAny(text, "inviter", "referrer", "inviteby", "邀请人", "推荐人")) return "INVITER";
  if (containsAny(text, "photoconsent", "portraitconsent", "imageconsent", "肖像授权", "拍照授权", "照片授权", "影像授权")) return "CONSENT_PHOTO";
  if (containsAny(text, "privacyconsent", "privacyagree", "agreement", "隐私同意", "隐私协议", "用户协议", "信息授权")) return "CONSENT_PRIVACY";
  if (containsAny(text, "roomremark", "房间备注", "住宿备注")) return "ROOM_REMARK";
  if (containsAny(text, "room", "房间号", "房号")) return "ROOM_NO";
  if (containsAny(text, "needroom", "accommodation", "hotel", "是否住宿", "住宿", "入住")) return "ACCOMMODATION_REQUIRED";
  if (containsAny(text, "remark", "note", "备注", "说明")) return "REMARK";
  return null;
}

export function normalizedAttendeeFieldText(mapping: { attendeeField?: string | null; tduckFieldKey?: string; tduckFieldLabel?: string }) {
  return normalizeFieldText(`${mapping.attendeeField || ""} ${mapping.tduckFieldKey || ""} ${mapping.tduckFieldLabel || ""}`);
}

function normalizeFieldText(value: string) {
  return value.toLowerCase().replace(/[_\-\s]+/g, "").trim();
}

function containsAny(value: string, ...candidates: string[]) {
  return candidates.some((candidate) => value.includes(normalizeFieldText(candidate)));
}
