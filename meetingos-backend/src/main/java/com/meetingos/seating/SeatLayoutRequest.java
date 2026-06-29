package com.meetingos.seating;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SeatLayoutRequest(
        @NotNull Long meetingId,
        @NotBlank String venueName,
        @NotBlank String areaName,
        String originalAreaName,
        @Min(1) int rowCount,
        @Min(1) int seatsPerRow,
        String numberingRule,
        String aisleSettings,
        String stageDirection,
        Integer sortOrder,
        SeatType defaultSeatType,
        List<String> disabledSeatCodes
) {
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
        this(
                meetingId,
                venueName,
                areaName,
                null,
                rowCount,
                seatsPerRow,
                numberingRule,
                aisleSettings,
                stageDirection,
                sortOrder,
                defaultSeatType,
                disabledSeatCodes
        );
    }
}
