package com.meetingos.seating;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SeatLayoutRequest(
        @NotNull Long meetingId,
        @NotBlank String venueName,
        @NotBlank String areaName,
        String originalAreaName,
        @Min(value = 1, message = "排数不能小于 1")
        @Max(value = MAX_ROWS, message = "单个区域最多支持 80 排")
        int rowCount,
        @Min(value = 1, message = "每排座位数不能小于 1")
        @Max(value = MAX_SEATS_PER_ROW, message = "单排最多支持 80 个座位")
        int seatsPerRow,
        String numberingRule,
        String aisleSettings,
        String stageDirection,
        Integer sortOrder,
        SeatType defaultSeatType,
        @Size(max = MAX_SEATS_PER_AREA, message = "禁用座位数量不能超过 6400")
        List<String> disabledSeatCodes,
        @Size(max = MAX_SEATS_PER_AREA, message = "结构空白数量不能超过 6400")
        List<String> structuralBlankSeatCodes,
        @Size(max = MAX_SEATS_PER_AREA, message = "过道数量不能超过 6400")
        List<String> aisleSeatCodes
) {
    public static final int MAX_ROWS = 80;
    public static final int MAX_SEATS_PER_ROW = 80;
    public static final int MAX_SEATS_PER_AREA = MAX_ROWS * MAX_SEATS_PER_ROW;

    public SeatLayoutRequest(Long meetingId,
                             String venueName,
                             String areaName,
                             String originalAreaName,
                             int rowCount,
                             int seatsPerRow,
                             String numberingRule,
                             String aisleSettings,
                             String stageDirection,
                             Integer sortOrder,
                             SeatType defaultSeatType,
                             List<String> disabledSeatCodes,
                             List<String> structuralBlankSeatCodes) {
        this(
                meetingId,
                venueName,
                areaName,
                originalAreaName,
                rowCount,
                seatsPerRow,
                numberingRule,
                aisleSettings,
                stageDirection,
                sortOrder,
                defaultSeatType,
                disabledSeatCodes,
                structuralBlankSeatCodes,
                null
        );
    }

    public SeatLayoutRequest(Long meetingId,
                             String venueName,
                             String areaName,
                             String originalAreaName,
                             int rowCount,
                             int seatsPerRow,
                             String numberingRule,
                             String aisleSettings,
                             String stageDirection,
                             Integer sortOrder,
                             SeatType defaultSeatType,
                             List<String> disabledSeatCodes) {
        this(
                meetingId,
                venueName,
                areaName,
                originalAreaName,
                rowCount,
                seatsPerRow,
                numberingRule,
                aisleSettings,
                stageDirection,
                sortOrder,
                defaultSeatType,
                disabledSeatCodes,
                null,
                null
        );
    }

    public SeatLayoutRequest(Long meetingId,
                             String venueName,
                             String areaName,
                             int rowCount,
                             int seatsPerRow,
                             String numberingRule,
                             String aisleSettings,
                             String stageDirection,
                             Integer sortOrder,
                             SeatType defaultSeatType,
                             List<String> disabledSeatCodes) {
        this(meetingId, venueName, areaName, null, rowCount, seatsPerRow, numberingRule, aisleSettings, stageDirection, sortOrder, defaultSeatType, disabledSeatCodes, null, null);
    }
}
