package com.meetingos.seating;

/**
 * 将网格位置标记为过道，或恢复为普通可用座位。
 */
public record SeatAisleRequest(
        boolean aisle
) {
}
