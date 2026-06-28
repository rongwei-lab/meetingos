package com.meetingos.statistics;

import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.CheckinStatus;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.checkin.CheckinRecordRepository;
import com.meetingos.checkin.CheckinResultStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.room.RoomRepository;
import com.meetingos.seating.SeatAssignmentRepository;
import com.meetingos.seating.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatisticsService {

    private final AttendeeRepository attendeeRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;
    private final CheckinRecordRepository checkinRecordRepository;

    public StatisticsService(AttendeeRepository attendeeRepository,
                             SeatAssignmentRepository seatAssignmentRepository,
                             SeatRepository seatRepository,
                             RoomRepository roomRepository,
                             CheckinRecordRepository checkinRecordRepository) {
        this.attendeeRepository = attendeeRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
        this.checkinRecordRepository = checkinRecordRepository;
    }

    @Transactional(readOnly = true)
    public StatisticsOverview overview(Long meetingId) {
        return overview(meetingId, null, null);
    }

    @Transactional(readOnly = true)
    public StatisticsOverview overview(Long meetingId, LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveRange(startDate, endDate);
        long registrations = attendeeRepository.countByMeetingId(meetingId);
        long pending = attendeeRepository.countByMeetingIdAndReviewStatus(meetingId, ReviewStatus.PENDING);
        long approved = attendeeRepository.countByMeetingIdAndReviewStatus(meetingId, ReviewStatus.APPROVED);
        long rejected = attendeeRepository.countByMeetingIdAndReviewStatus(meetingId, ReviewStatus.REJECTED);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayRegistrations = attendeeRepository.countByMeetingIdAndRegisteredAtBetween(meetingId, todayStart, todayStart.plusDays(1));
        long totalSeats = seatRepository.countByMeetingId(meetingId);
        long disabledSeats = seatRepository.countByMeetingIdAndDisabledTrue(meetingId);
        long seated = seatAssignmentRepository.countByMeetingId(meetingId);
        long availableSeats = Math.max(0, totalSeats - disabledSeats);
        long emptySeats = Math.max(0, availableSeats - seated);
        double seatUsageRate = availableSeats == 0 ? 0 : (double) seated / availableSeats;
        long roomCount = roomRepository.countByMeetingId(meetingId);
        long roomAssigned = attendeeRepository.countByMeetingIdAndRoomNoIsNotNull(meetingId);
        long roomUnassigned = attendeeRepository.countByMeetingIdAndRoomNoIsNull(meetingId);
        long accommodationRequired = attendeeRepository.countByMeetingIdAndAccommodationRequiredTrue(meetingId);
        long roomAssignedRequired = attendeeRepository.countByMeetingIdAndAccommodationRequiredTrueAndRoomNoIsNotNull(meetingId);
        long roomUnassignedRequired = attendeeRepository.countByMeetingIdAndAccommodationRequiredTrueAndRoomNoIsNull(meetingId);
        long checkedIn = attendeeRepository.countByMeetingIdAndCheckinStatus(meetingId, CheckinStatus.CHECKED_IN);
        long notCheckedIn = Math.max(0, approved - checkedIn);
        double rate = approved == 0 ? 0 : (double) checkedIn / approved;
        long rangeRegistrations = attendeeRepository.countByMeetingIdAndRegisteredAtBetween(meetingId, range.startDateTime(), range.exclusiveEndDateTime());
        long rangeCheckins = checkinRecordRepository.countByMeetingIdAndResultStatusAndCheckedInAtBetween(
                meetingId,
                CheckinResultStatus.SUCCESS,
                range.startDateTime(),
                range.exclusiveEndDateTime()
        );
        return new StatisticsOverview(
                registrations,
                pending,
                approved,
                rejected,
                todayRegistrations,
                totalSeats,
                availableSeats,
                seated,
                emptySeats,
                disabledSeats,
                seatUsageRate,
                roomCount,
                accommodationRequired,
                roomAssignedRequired,
                roomUnassignedRequired,
                roomAssigned,
                roomUnassigned,
                checkedIn,
                notCheckedIn,
                rate,
                rangeRegistrations,
                rangeCheckins,
                toDateSeries(range, attendeeRepository.countRegistrationsByDate(meetingId, range.startDateTime(), range.exclusiveEndDateTime())),
                toDateSeries(range, checkinRecordRepository.countSuccessByDate(meetingId, range.startDateTime(), range.exclusiveEndDateTime())),
                toDistribution(attendeeRepository.countByReviewStatus(meetingId)),
                toDistribution(attendeeRepository.countByAttendeeType(meetingId)),
                toDistribution(attendeeRepository.countByOrganization(meetingId)),
                toDistribution(roomRepository.countByHotelName(meetingId)),
                toDistribution(roomRepository.countByRoomType(meetingId)),
                toDistribution(checkinRecordRepository.countSuccessByEntrance(meetingId)),
                toDistribution(checkinRecordRepository.countSuccessByType(meetingId))
        );
    }

    private Map<String, Long> toDistribution(Iterable<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] == null ? "未填写" : String.valueOf(row[0]);
            Long value = row[1] instanceof Number number ? number.longValue() : 0L;
            result.put(key, value);
        }
        return result;
    }

    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        LocalDate resolvedEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(6) : startDate;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw BusinessException.badRequest("统计开始日期不能晚于结束日期");
        }
        if (resolvedStart.plusDays(366).isBefore(resolvedEnd)) {
            throw BusinessException.badRequest("统计时间范围不能超过 366 天");
        }
        return new DateRange(resolvedStart, resolvedEnd);
    }

    private Map<String, Long> toDateSeries(DateRange range, Iterable<Object[]> rows) {
        Map<String, Long> actual = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            actual.put(String.valueOf(row[0]), row[1] instanceof Number number ? number.longValue() : 0L);
        }

        /*
         * 前端趋势图需要连续横轴。数据库只返回有数据的日期，这里补齐 0，
         * 避免某天没有报名/签到时图表断点或横轴长度变化。
         */
        Map<String, Long> series = new LinkedHashMap<>();
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            String key = date.toString();
            series.put(key, actual.getOrDefault(key, 0L));
        }
        return series;
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime() {
            return startDate.atStartOfDay();
        }

        LocalDateTime exclusiveEndDateTime() {
            return endDate.plusDays(1).atStartOfDay();
        }
    }
}
