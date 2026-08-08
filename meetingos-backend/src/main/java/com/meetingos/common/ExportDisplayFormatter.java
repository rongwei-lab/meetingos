package com.meetingos.common;

import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.checkin.CheckinResultStatus;
import com.meetingos.checkin.CheckinType;
import com.meetingos.room.RoomStatus;
import com.meetingos.seating.SeatStatus;
import com.meetingos.seating.SeatType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导出文件的展示值格式化器。
 *
 * <p>接口和数据库内部继续使用枚举/时间类型，导出面向人工阅读，统一在这里把常见枚举转成中文、
 * 把 LocalDateTime 转成不带 T/纳秒的秒级时间，避免 CSV 和 XLSX 入口各自散落一套格式规则。</p>
 */
public final class ExportDisplayFormatter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_LIKE_TEXT = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})(?:[T\\s](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?)?$"
    );

    private ExportDisplayFormatter() {
    }

    public static String value(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME_FORMATTER);
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE_FORMATTER);
        }
        if (value instanceof Boolean bool) {
            return bool ? "是" : "否";
        }
        if (value instanceof AttendeeType attendeeType) {
            return attendeeType(attendeeType);
        }
        if (value instanceof ReviewStatus reviewStatus) {
            return reviewStatus(reviewStatus);
        }
        if (value instanceof CheckinStatus checkinStatus) {
            return checkinStatus(checkinStatus);
        }
        if (value instanceof CheckinType checkinType) {
            return checkinType(checkinType);
        }
        if (value instanceof CheckinResultStatus resultStatus) {
            return checkinResultStatus(resultStatus);
        }
        if (value instanceof RoomStatus roomStatus) {
            return roomStatus(roomStatus);
        }
        if (value instanceof SeatType seatType) {
            return seatType(seatType);
        }
        if (value instanceof SeatStatus seatStatus) {
            return seatStatus(seatStatus);
        }
        if (value instanceof String text) {
            return dateLikeText(text);
        }
        return String.valueOf(value);
    }

    public static String attendeeType(AttendeeType value) {
        if (value == null) return "";
        return switch (value) {
            case VIP -> "VIP";
            case GUEST -> "嘉宾";
            case MEDIA -> "媒体";
            case STAFF -> "工作人员";
            case NORMAL -> "普通参会人";
        };
    }

    public static String attendeeType(String value) {
        AttendeeType parsed = parseEnum(AttendeeType.class, value);
        return parsed == null ? fallbackText(value) : attendeeType(parsed);
    }

    public static String reviewStatus(ReviewStatus value) {
        if (value == null) return "";
        return switch (value) {
            case PENDING -> "待审核";
            case APPROVED -> "已通过";
            case REJECTED -> "已拒绝";
            case CANCELLED -> "已取消";
        };
    }

    public static String reviewStatus(String value) {
        ReviewStatus parsed = parseEnum(ReviewStatus.class, value);
        return parsed == null ? fallbackText(value) : reviewStatus(parsed);
    }

    public static String checkinStatus(CheckinStatus value) {
        if (value == null) return "";
        return switch (value) {
            case NOT_CHECKED_IN -> "未签到";
            case CHECKED_IN -> "已签到";
        };
    }

    public static String checkinType(CheckinType value) {
        if (value == null) return "";
        return switch (value) {
            case SCAN -> "扫码签到";
            case SEARCH -> "查询签到";
            case MANUAL -> "手动签到";
            case SUPPLEMENT -> "补签";
        };
    }

    public static String checkinType(String value) {
        CheckinType parsed = parseEnum(CheckinType.class, value);
        return parsed == null ? fallbackText(value) : checkinType(parsed);
    }

    public static String checkinResultStatus(CheckinResultStatus value) {
        if (value == null) return "";
        return switch (value) {
            case SUCCESS -> "签到成功";
            case DUPLICATE -> "重复签到";
            case NOT_APPROVED -> "未审核通过";
            case NOT_FOUND -> "未找到参会人";
        };
    }

    public static String roomStatus(RoomStatus value) {
        if (value == null) return "";
        return switch (value) {
            case AVAILABLE -> "可分配";
            case ASSIGNED -> "已分配";
            case DISABLED -> "已停用";
        };
    }

    public static String seatType(SeatType value) {
        if (value == null) return "";
        return switch (value) {
            case VIP -> "VIP";
            case GUEST -> "嘉宾";
            case MEDIA -> "媒体";
            case STAFF -> "工作人员";
            case NORMAL -> "普通座";
            case DISABLED -> "禁用座";
        };
    }

    public static String seatStatus(SeatStatus value) {
        if (value == null) return "";
        return switch (value) {
            case EMPTY -> "空座";
            case ASSIGNED -> "已分配";
            case UNREGISTERED -> "未报名入座";
            case CHECKED_IN -> "已签到";
            case DISABLED -> "禁用";
            case AISLE -> "过道";
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String fallbackText(String value) {
        return value == null || value.isBlank() ? "未填写" : value;
    }

    private static String dateLikeText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.trim();
        Matcher matcher = DATE_LIKE_TEXT.matcher(text);
        if (!matcher.matches()) {
            return value;
        }
        String date = matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        String hour = matcher.group(4);
        String minute = matcher.group(5);
        if (hour == null || minute == null) {
            return date;
        }
        String second = matcher.group(6) == null ? "00" : matcher.group(6);
        return date + " " + hour + ":" + minute + ":" + second;
    }
}
