package com.meetingos.operationlog;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 日志字段存的是稳定动作码；这里集中维护其中文别名，供后端关键词搜索使用。
 * 前端只负责展示，不能成为后端中文检索能力的唯一来源。
 */
final class OperationLogCatalog {

    private static final Map<String, String> ACTION_LABELS = actionLabels();
    private static final Map<String, Set<String>> ACTION_ALIASES = actionAliases();
    private static final Map<String, String> TARGET_TYPE_LABELS = targetTypeLabels();
    private static final Map<String, Set<String>> TARGET_TYPE_ALIASES = targetTypeAliases();

    private OperationLogCatalog() {
    }

    static Set<String> matchingActions(String keyword) {
        return matchingCodes(ACTION_LABELS, ACTION_ALIASES, keyword);
    }

    static Set<String> matchingTargetTypes(String keyword) {
        return matchingCodes(TARGET_TYPE_LABELS, TARGET_TYPE_ALIASES, keyword);
    }

    static Set<OperationLogCategory> matchingCategories(String keyword) {
        String normalized = normalize(keyword);
        Set<OperationLogCategory> matches = new LinkedHashSet<>();
        for (OperationLogCategory category : OperationLogCategory.values()) {
            if (containsIgnoreCase(category.name(), normalized)
                    || containsIgnoreCase(category.getLabel(), normalized)) {
                matches.add(category);
            }
        }
        return matches;
    }

    static boolean matchesSuccess(String keyword) {
        String normalized = normalize(keyword);
        return containsIgnoreCase("成功", normalized) || containsIgnoreCase("SUCCESS", normalized);
    }

    static boolean matchesFailure(String keyword) {
        String normalized = normalize(keyword);
        return containsIgnoreCase("失败", normalized) || containsIgnoreCase("FAILURE", normalized);
    }

    private static Set<String> matchingCodes(Map<String, String> labels, Map<String, Set<String>> aliases,
                                             String keyword) {
        String normalized = normalize(keyword);
        Set<String> matches = new LinkedHashSet<>();
        labels.forEach((code, label) -> {
            boolean aliasMatched = aliases.getOrDefault(code, Set.of()).stream()
                    .anyMatch(alias -> containsIgnoreCase(alias, normalized));
            if (containsIgnoreCase(code, normalized) || containsIgnoreCase(label, normalized) || aliasMatched) {
                matches.add(code);
            }
        });
        return matches;
    }

    private static boolean containsIgnoreCase(String source, String keyword) {
        return source.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, String> actionLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("AUTH_LOGIN", "登录");
        labels.put("AUTH_LOGOUT", "退出登录");
        labels.put("USER_CREATE", "新建用户");
        labels.put("USER_UPDATE", "更新用户");
        labels.put("USER_RESET_PASSWORD", "重置用户密码");
        labels.put("MEETING_PERMISSION_GRANT", "授予会议权限");
        labels.put("MEETING_PERMISSION_DELETE", "移除会议权限");

        labels.put("MEETING_CREATE", "创建会议");
        labels.put("MEETING_UPDATE", "更新会议");
        labels.put("MEETING_QUERY_SETTINGS_UPDATE", "更新查询设置");
        labels.put("MEETING_PRINT_SETTINGS_UPDATE", "更新打印设置");
        labels.put("MEETING_STATUS_CHANGE", "修改会议状态");
        labels.put("MEETING_PUBLISH", "发布会议");
        labels.put("MEETING_RESTART", "重新开放会议");
        labels.put("MEETING_CLOSE_REGISTRATION", "关闭报名");
        labels.put("MEETING_READY", "会议就绪");
        labels.put("MEETING_START", "开始会议");
        labels.put("MEETING_END", "结束会议");
        labels.put("MEETING_ARCHIVE", "归档会议");
        labels.put("MEETING_DELETE_ARCHIVE", "删除归档会议");
        labels.put("DASHBOARD_CONFIG_UPDATE", "更新统计看板配置");
        labels.put("NOTIFICATION_CREATE", "创建通知");

        labels.put("ATTENDEE_CREATE", "新增参会人");
        labels.put("ATTENDEE_UPDATE", "更新参会人");
        labels.put("ATTENDEE_REVIEW", "审核报名");
        labels.put("ATTENDEE_BATCH_REVIEW", "批量审核报名");
        labels.put("ATTENDEE_TAGS_UPDATE", "更新参会人标签");
        // 兼容旧版接口曾经写入的动作码，避免历史日志无法按中文名称检索。
        labels.put("ATTENDEE_UPDATE_TAGS", "更新参会人标签");
        labels.put("ATTENDEE_REMARK_UPDATE", "更新参会人备注");
        labels.put("ATTENDEE_CANCEL", "取消参会人");
        labels.put("ATTENDEE_IMPORT", "导入参会人");
        labels.put("ATTENDEE_EXPORT", "导出参会人");
        labels.put("CHECKIN", "签到");
        labels.put("CHECKIN_SUPPLEMENT", "补签");
        labels.put("PUBLIC_SELF_CHECKIN", "自助签到");

        labels.put("ROOM_CREATE", "新增房间");
        labels.put("ROOM_UPDATE", "更新房间");
        labels.put("ROOM_ASSIGN", "分配房间");
        labels.put("ROOM_CLEAR", "解除房间分配");
        labels.put("ROOM_ASSIGNMENT_CLEAR", "解除房间分配");
        labels.put("ROOM_ASSIGNMENT_CHANGE", "房间分配变更");
        labels.put("ROOM_IMPORT", "导入房间");
        labels.put("ROOM_ASSIGNMENT_IMPORT", "导入房间分配");
        labels.put("ROOM_EXPORT", "导出房间");
        labels.put("SEATING_CREATE_LAYOUT", "生成座位图");
        labels.put("SEATING_DELETE_AREA", "删除座位区域");
        labels.put("SEATING_AUTO_ASSIGN", "自动排座");
        labels.put("SEATING_MANUAL_ASSIGN", "手动分配座位");
        labels.put("SEATING_SWAP", "交换座位");
        labels.put("SEATING_CLEAR", "解除座位分配");
        labels.put("SEATING_LOCK_SEAT", "锁定座位");
        labels.put("SEATING_UPDATE_SEAT_TYPE", "修改座位类型");
        labels.put("SEATING_TOGGLE_SEAT_DISABLED", "启停座位");
        labels.put("DINNER_SETTINGS_UPDATE", "更新晚宴设置");
        labels.put("DINNER_TABLE_CREATE", "新增晚宴桌");
        labels.put("DINNER_TABLE_UPDATE", "更新晚宴桌");
        labels.put("DINNER_TABLE_DELETE", "删除晚宴桌");
        labels.put("DINNER_ASSIGN", "分配晚宴桌");
        labels.put("DINNER_ASSIGNMENT_CLEAR", "解除晚宴桌分配");
        labels.put("DINNER_ASSIGNMENT_CHANGE", "晚宴桌分配变更");
        labels.put("DINNER_ASSIGNMENT_IMPORT", "导入晚宴桌分配");
        labels.put("DINNER_EXPORT", "导出晚宴安排");

        labels.put("TDUCK_BIND_FORM", "绑定 TDuck 表单");
        labels.put("TDUCK_REBIND_FORM", "更换 TDuck 表单");
        labels.put("TDUCK_REGISTRATION_TOGGLE", "启停报名入口");
        labels.put("TDUCK_CREATE_FORM", "创建 TDuck 表单");
        labels.put("TDUCK_UPDATE_FIELD_MAPPING", "更新 TDuck 字段映射");
        labels.put("TDUCK_SYNC_FIELD_MAPPING", "同步 TDuck 字段映射");
        labels.put("TDUCK_SYNC_FIELDS", "同步 TDuck 字段");
        labels.put("TDUCK_MANUAL_SYNC", "手动同步 TDuck 报名");
        labels.put("TDUCK_PULL_SYNC", "拉取同步 TDuck 报名");
        labels.put("TDUCK_BATCH_PULL_SYNC", "批量拉取 TDuck 报名");
        labels.put("TDUCK_WEBHOOK_SYNC", "TDuck Webhook 同步");
        labels.put("TDUCK_DUPLICATE_CONFLICT_RESOLVE", "处理 TDuck 重复报名");

        labels.put("INVITATION_CREATE", "创建邀请函");
        labels.put("INVITATION_COPY", "复制邀请函");
        labels.put("INVITATION_DRAFT_SAVE", "保存邀请函草稿");
        labels.put("INVITATION_PUBLISH", "发布邀请函");
        labels.put("INVITATION_UNPUBLISH", "下线邀请函");
        labels.put("INVITATION_ARCHIVE", "归档邀请函");
        labels.put("INVITATION_SHARE_LINK_CREATE", "创建邀请函渠道链接");
        labels.put("INVITATION_SHARE_LINK_UPDATE", "更新邀请函渠道链接");
        labels.put("INVITATION_ASSET_UPLOAD", "上传邀请函素材");

        labels.put("IMPORT_TASK_CREATE", "创建导入任务");
        labels.put("IMPORT_TASK_COMMIT", "提交导入任务");
        labels.put("IMPORT_FAILURE_DOWNLOAD", "下载导入失败记录");
        labels.put("EXPORT_CREATE", "创建导出任务");
        labels.put("EXPORT_DOWNLOAD", "下载导出文件");
        labels.put("SYSTEM_SETTINGS_UPDATE", "更新系统设置");
        return Map.copyOf(labels);
    }

    private static Map<String, Set<String>> actionAliases() {
        return Map.ofEntries(
                Map.entry("ATTENDEE_TAGS_UPDATE", Set.of("更新标签")),
                Map.entry("ATTENDEE_UPDATE_TAGS", Set.of("更新标签")),
                Map.entry("DINNER_SETTINGS_UPDATE", Set.of("更新用餐设置")),
                Map.entry("DINNER_TABLE_CREATE", Set.of("新增餐桌")),
                Map.entry("DINNER_TABLE_UPDATE", Set.of("更新餐桌")),
                Map.entry("DINNER_TABLE_DELETE", Set.of("删除餐桌")),
                Map.entry("DINNER_ASSIGN", Set.of("分配用餐座位")),
                Map.entry("DINNER_ASSIGNMENT_CLEAR", Set.of("解除用餐分配")),
                Map.entry("DINNER_ASSIGNMENT_CHANGE", Set.of("调整用餐分配")),
                Map.entry("DINNER_ASSIGNMENT_IMPORT", Set.of("导入用餐分配")),
                Map.entry("DINNER_EXPORT", Set.of("导出用餐分配")),
                Map.entry("TDUCK_MANUAL_SYNC", Set.of("手动同步 TDuck 数据")),
                Map.entry("TDUCK_PULL_SYNC", Set.of("拉取同步 TDuck 数据")),
                Map.entry("TDUCK_BATCH_PULL_SYNC", Set.of("批量拉取 TDuck 数据")),
                Map.entry("TDUCK_DUPLICATE_CONFLICT_RESOLVE", Set.of(
                        "处理重复报名",
                        "重复报名处理",
                        "Resolve TDuck duplicate registration",
                        "TDuck duplicate conflict"
                ))
        );
    }

    private static Map<String, String> targetTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("APP_USER", "用户");
        labels.put("AUTH_SESSION", "登录会话");
        labels.put("MEETING_USER_PERMISSION", "会议用户权限");
        labels.put("MEETING", "会议");
        labels.put("MEETING_DASHBOARD", "会议统计看板");
        labels.put("NOTIFICATION", "通知");
        labels.put("ATTENDEE", "参会人");
        labels.put("ROOM", "房间");
        labels.put("SEAT", "座位");
        labels.put("SEAT_LAYOUT", "座位布局");
        labels.put("SEAT_ASSIGNMENT", "座位分配");
        labels.put("DINNER", "晚宴安排");
        labels.put("DINNER_TABLE", "晚宴桌");
        labels.put("TDUCK_FORM", "TDuck 表单");
        labels.put("TDUCK_MAPPING", "TDuck 字段映射");
        labels.put("TDUCK_DATA", "TDuck 报名数据");
        labels.put("TDUCK_SYNC", "TDuck 同步");
        labels.put("MEETING_INVITATION", "邀请函");
        labels.put("INVITATION_SHARE_LINK", "邀请函渠道链接");
        labels.put("INVITATION_ASSET", "邀请函素材");
        labels.put("IMPORT_TASK", "导入任务");
        labels.put("EXPORT_TASK", "导出任务");
        labels.put("SYSTEM_SETTINGS", "系统设置");
        // 早期签到接口曾写入该对象类型，仍应支持用中文对象名称检索。
        labels.put("CHECKIN", "签到记录");
        return Map.copyOf(labels);
    }

    private static Map<String, Set<String>> targetTypeAliases() {
        return Map.of(
                "MEETING_USER_PERMISSION", Set.of("会议权限"),
                "DINNER", Set.of("用餐安排"),
                "DINNER_TABLE", Set.of("餐桌"),
                "TDUCK_DATA", Set.of("TDuck 数据")
        );
    }
}
