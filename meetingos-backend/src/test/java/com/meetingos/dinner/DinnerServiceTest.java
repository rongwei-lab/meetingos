package com.meetingos.dinner;

import com.meetingos.attendee.Attendee;
import com.meetingos.attendee.AttendeeRepository;
import com.meetingos.attendee.AttendeeType;
import com.meetingos.attendee.ReviewStatus;
import com.meetingos.common.BusinessException;
import com.meetingos.common.ExportTable;
import com.meetingos.common.ImportResult;
import com.meetingos.common.SpreadsheetFiles;
import com.meetingos.meeting.Meeting;
import com.meetingos.meeting.MeetingRepository;
import com.meetingos.operationlog.OperationLogService;
import com.meetingos.settings.SystemSettingsService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DinnerServiceTest {

    private MeetingRepository meetingRepository;
    private AttendeeRepository attendeeRepository;
    private DinnerAssignmentRepository dinnerAssignmentRepository;
    private DinnerTableRepository dinnerTableRepository;
    private OperationLogService operationLogService;
    private SystemSettingsService systemSettingsService;
    private DinnerService dinnerService;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meetingRepository = mock(MeetingRepository.class);
        attendeeRepository = mock(AttendeeRepository.class);
        dinnerAssignmentRepository = mock(DinnerAssignmentRepository.class);
        dinnerTableRepository = mock(DinnerTableRepository.class);
        operationLogService = mock(OperationLogService.class);
        systemSettingsService = mock(SystemSettingsService.class);
        dinnerService = new DinnerService(
                meetingRepository,
                attendeeRepository,
                dinnerAssignmentRepository,
                dinnerTableRepository,
                operationLogService,
                systemSettingsService
        );

        meeting = new Meeting();
        meeting.setId(1L);
        meeting.setName("数字经济论坛");
        meeting.setDinnerVenue("三层宴会厅");
        meeting.setDinnerTime("18:30");
        when(meetingRepository.findById(1L)).thenReturn(Optional.of(meeting));
        when(dinnerTableRepository.findMeetingByIdForUpdate(1L)).thenReturn(Optional.of(meeting));
        when(dinnerTableRepository.findMeetingIdById(300L)).thenReturn(Optional.of(1L));
        when(dinnerAssignmentRepository.findMeetingIdByAttendeeId(11L)).thenReturn(Optional.of(1L));
        when(dinnerAssignmentRepository.save(any(DinnerAssignment.class))).thenAnswer(invocation -> {
            DinnerAssignment assignment = invocation.getArgument(0);
            if (assignment.getId() == null) {
                assignment.setId(100L);
            }
            return assignment;
        });
        when(dinnerTableRepository.save(any(DinnerTable.class))).thenAnswer(invocation -> {
            DinnerTable table = invocation.getArgument(0);
            if (table.getId() == null) {
                table.setId(300L);
            }
            return table;
        });
        when(dinnerTableRepository.saveAndFlush(any(DinnerTable.class))).thenAnswer(invocation -> {
            DinnerTable table = invocation.getArgument(0);
            if (table.getId() == null) {
                table.setId(300L);
            }
            return table;
        });
    }

    @Test
    void importAssignmentsMatchesAttendeeByPhone() {
        meeting.setDinnerVenue(null);
        meeting.setDinnerTime(null);
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        when(attendeeRepository.findByMeetingIdAndPhone(1L, "13800000000")).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(dinnerAssignmentRepository.findByAttendeeIdForUpdate(11L)).thenReturn(Optional.empty());
        when(dinnerTableRepository.findByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(Optional.empty());

        ImportResult result = dinnerService.importAssignments(1L, workbook(
                List.of("姓名", "手机号", "单位", "桌号", "晚宴地点", "晚宴时间", "备注"),
                List.of("张三", "13800000000", "示例公司", "3号桌", "三层宴会厅", "18:30", "主桌")
        ));

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isZero();
        assertThat(meeting.getDinnerVenue()).isEqualTo("三层宴会厅");
        assertThat(meeting.getDinnerTime()).isEqualTo("18:30");
        verify(dinnerAssignmentRepository).save(any(DinnerAssignment.class));
    }

    @Test
    void updateSettingsStoresVenueAndTimeOnMeeting() {
        DinnerSettingsResponse response = dinnerService.updateSettings(new DinnerSettingsRequest(1L, "二层牡丹厅", "19:00"));

        assertThat(response.venue()).isEqualTo("二层牡丹厅");
        assertThat(response.dinnerTime()).isEqualTo("19:00");
        assertThat(meeting.getDinnerVenue()).isEqualTo("二层牡丹厅");
        assertThat(meeting.getDinnerTime()).isEqualTo("19:00");
    }

    @Test
    void importAssignmentsReportsBusinessFailures() {
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        when(attendeeRepository.findByMeetingIdAndPhone(1L, "13900000000")).thenReturn(Optional.empty());
        when(attendeeRepository.findByMeetingIdAndPhone(1L, "13800000000")).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByMeetingIdAndNameAndOrganization(1L, "李四", "示例公司"))
                .thenReturn(List.of(
                        attendee(21L, "李四", "13600000001", "示例公司", ReviewStatus.APPROVED),
                        attendee(22L, "李四", "13600000002", "示例公司", ReviewStatus.APPROVED)
                ));

        ImportResult result = dinnerService.importAssignments(1L, workbook(
                List.of("手机号", "姓名", "单位", "桌号"),
                List.of("13900000000", "", "", "1号桌"),
                List.of("", "李四", "示例公司", "2号桌"),
                List.of("13800000000", "", "", "")
        ));

        assertThat(result.importedRows()).isZero();
        assertThat(result.skippedRows()).isEqualTo(3);
        assertThat(result.failedRows())
                .extracting(failure -> failure.reason())
                .anyMatch(reason -> reason.contains("未找到手机号对应参会人"))
                .anyMatch(reason -> reason.contains("姓名+单位匹配到多名参会人"))
                .anyMatch(reason -> reason.contains("桌号"));
    }

    @Test
    void importAssignmentsUpdatesExistingRecordForSameAttendee() {
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        DinnerAssignment existing = assignment(200L, attendee, "1号桌", "旧宴会厅", "18:00", "旧备注");
        DinnerTable targetTable = table(300L, "8号桌", 10);
        when(attendeeRepository.findByMeetingIdAndPhone(1L, "13800000000")).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(dinnerAssignmentRepository.findByAttendeeIdForUpdate(11L)).thenReturn(Optional.of(existing));
        when(dinnerTableRepository.findByMeetingIdAndTableNo(1L, "8号桌")).thenReturn(Optional.of(targetTable));
        when(dinnerTableRepository.findByMeetingIdAndTableNoInForUpdate(1L, List.of("1号桌", "8号桌")))
                .thenReturn(List.of(targetTable));

        ImportResult result = dinnerService.importAssignments(1L, workbook(
                List.of("手机号", "桌号", "晚宴地点", "晚宴时间", "备注"),
                List.of("13800000000", "8号桌", "三层宴会厅", "18:30", "调整")
        ));

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(existing.getId()).isEqualTo(200L);
        assertThat(existing.getTableNo()).isEqualTo("8号桌");
        assertThat(existing.getVenue()).isEqualTo("旧宴会厅");
        assertThat(existing.getDinnerTime()).isEqualTo("18:00");
        assertThat(meeting.getDinnerVenue()).isEqualTo("三层宴会厅");
        assertThat(meeting.getDinnerTime()).isEqualTo("18:30");
        assertThat(existing.getRemark()).isEqualTo("调整");
        var expectedLogDetails = Map.<String, Object>of(
                "meetingId", 1L,
                "attendeeId", 11L,
                "attendeeName", "张三",
                "oldTableNo", "1号桌",
                "newTableNo", "8号桌",
                "source", "IMPORT",
                "remark", "调整"
        );
        verify(operationLogService).recordWithinTransaction(
                "DINNER_ASSIGNMENT_CHANGE",
                "DINNER",
                "11",
                true,
                "晚宴桌号变更",
                null,
                expectedLogDetails
        );
        verify(operationLogService, never()).record(
                "DINNER_ASSIGNMENT_CHANGE",
                "DINNER",
                "11",
                true,
                "晚宴桌号变更",
                null,
                expectedLogDetails
        );
    }

    @Test
    void exportDinnerTableContainsExpectedColumnsAndRows() {
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        attendee.setTitle("总监");
        attendee.setAttendeeType(AttendeeType.VIP);
        DinnerAssignment assignment = assignment(200L, attendee, "3号桌", null, null, "主桌");
        when(systemSettingsService.maskSensitiveEnabled()).thenReturn(false);
        when(dinnerAssignmentRepository.findWithAttendeeByMeetingIdOrderByTableNoAscIdAsc(1L)).thenReturn(List.of(assignment));

        ExportTable table = dinnerService.exportDinnerTable(1L);

        assertThat(table.headers()).containsExactly("姓名", "手机号", "参会码", "单位", "职务", "参会类型", "桌号", "晚宴地点", "晚宴时间", "备注");
        assertThat(table.rows()).hasSize(1);
        assertThat(table.rows().get(0)).isEqualTo(List.of(
                "张三", "13800000000", "FORUM-11", "示例公司", "总监", AttendeeType.VIP, "3号桌", "三层宴会厅", "18:30", "主桌"
        ));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(SpreadsheetFiles.toXlsx(List.of(table))))) {
            assertThat(workbook.getSheet("晚宴安排").getRow(1).getCell(5).getStringCellValue()).isEqualTo("VIP");
        } catch (java.io.IOException ex) {
            throw new AssertionError("晚宴导出 XLSX 读取失败", ex);
        }
    }

    @Test
    void listTablesReturnsOccupiedAndRemainingCounts() {
        DinnerTable table = table(300L, "3号桌", 10);
        when(dinnerTableRepository.findByMeetingIdOrderByTableNoAsc(1L)).thenReturn(List.of(table));
        when(dinnerAssignmentRepository.countByMeetingIdGroupByTableNo(1L))
                .thenReturn(List.<Object[]>of(new Object[]{"3号桌", 7L}));

        List<DinnerTableResponse> tables = dinnerService.listTables(1L);

        assertThat(tables).hasSize(1);
        DinnerTableResponse response = tables.get(0);
        assertThat(response.tableNo()).isEqualTo("3号桌");
        assertThat(response.capacity()).isEqualTo(10);
        assertThat(response.occupiedCount()).isEqualTo(7);
        assertThat(response.remainingCount()).isEqualTo(3);
        assertThat(response.overCapacityCount()).isZero();
    }

    @Test
    void assignRejectsFullDinnerTable() {
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        DinnerTable targetTable = table(300L, "3号桌", 1);
        when(attendeeRepository.findById(11L)).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(dinnerAssignmentRepository.findByAttendeeIdForUpdate(11L)).thenReturn(Optional.empty());
        when(dinnerTableRepository.findByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(Optional.of(targetTable));
        when(dinnerTableRepository.findByMeetingIdAndTableNoInForUpdate(1L, List.of("3号桌")))
                .thenReturn(List.of(targetTable));
        when(dinnerAssignmentRepository.countByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(1L);

        assertThatThrownBy(() -> dinnerService.assign(new DinnerAssignRequest(11L, "3号桌", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void assignLocksDinnerTableBeforeCapacityCheckAndWrite() {
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        DinnerTable targetTable = table(300L, "3号桌", 10);
        when(attendeeRepository.findById(11L)).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(dinnerAssignmentRepository.findByAttendeeIdForUpdate(11L)).thenReturn(Optional.empty());
        when(dinnerTableRepository.findByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(Optional.of(targetTable));
        when(dinnerTableRepository.findByMeetingIdAndTableNoInForUpdate(1L, List.of("3号桌")))
                .thenReturn(List.of(targetTable));
        when(dinnerAssignmentRepository.countByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(0L);

        dinnerService.assign(new DinnerAssignRequest(11L, "3号桌", null));

        var ordered = inOrder(attendeeRepository, dinnerAssignmentRepository, dinnerTableRepository);
        ordered.verify(dinnerTableRepository).findMeetingByIdForUpdate(1L);
        ordered.verify(attendeeRepository).findByIdForUpdate(11L);
        ordered.verify(dinnerTableRepository).findByMeetingIdAndTableNoInForUpdate(1L, List.of("3号桌"));
        ordered.verify(dinnerAssignmentRepository).countByMeetingIdAndTableNo(1L, "3号桌");
        ordered.verify(dinnerAssignmentRepository).save(any(DinnerAssignment.class));
    }

    @Test
    void updateTableLocksMeetingAndTargetTableBeforeCapacityCheck() {
        DinnerTable targetTable = table(300L, "3号桌", 10);
        when(dinnerTableRepository.findById(300L)).thenReturn(Optional.of(targetTable));
        when(dinnerTableRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(targetTable));
        when(dinnerTableRepository.findByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(Optional.of(targetTable));
        when(dinnerAssignmentRepository.countByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(2L);

        dinnerService.updateTable(300L, new DinnerTableRequest(1L, "3号桌", 8, "靠近主舞台"));

        var ordered = inOrder(dinnerTableRepository, dinnerAssignmentRepository);
        ordered.verify(dinnerTableRepository).findMeetingByIdForUpdate(1L);
        ordered.verify(dinnerTableRepository).findByIdForUpdate(300L);
        ordered.verify(dinnerAssignmentRepository).countByMeetingIdAndTableNo(1L, "3号桌");
        ordered.verify(dinnerTableRepository).save(targetTable);
    }

    @Test
    void deleteTableLocksTargetTableBeforeOccupancyCheck() {
        DinnerTable targetTable = table(300L, "3号桌", 10);
        when(dinnerTableRepository.findById(300L)).thenReturn(Optional.of(targetTable));
        when(dinnerTableRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(targetTable));
        when(dinnerAssignmentRepository.countByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(0L);

        dinnerService.deleteTable(1L, 300L);

        var ordered = inOrder(dinnerTableRepository, dinnerAssignmentRepository);
        ordered.verify(dinnerTableRepository).findByIdForUpdate(300L);
        ordered.verify(dinnerAssignmentRepository).countByMeetingIdAndTableNo(1L, "3号桌");
        ordered.verify(dinnerTableRepository).delete(targetTable);
    }

    @Test
    void clearLocksAttendeeAssignmentAndPreviousTableBeforeDelete() {
        Attendee attendee = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        DinnerAssignment assignment = assignment(200L, attendee, "3号桌", null, null, "主桌");
        DinnerTable previousTable = table(300L, "3号桌", 10);
        when(attendeeRepository.findById(11L)).thenReturn(Optional.of(attendee));
        when(attendeeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(attendee));
        when(dinnerAssignmentRepository.findByAttendeeIdForUpdate(11L)).thenReturn(Optional.of(assignment));
        when(dinnerTableRepository.findByMeetingIdAndTableNo(1L, "3号桌")).thenReturn(Optional.of(previousTable));
        when(dinnerTableRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(previousTable));

        dinnerService.clear(11L);

        var ordered = inOrder(attendeeRepository, dinnerAssignmentRepository, dinnerTableRepository);
        ordered.verify(dinnerTableRepository).findMeetingByIdForUpdate(1L);
        ordered.verify(attendeeRepository).findByIdForUpdate(11L);
        ordered.verify(dinnerAssignmentRepository).findByAttendeeIdForUpdate(11L);
        ordered.verify(dinnerTableRepository).findByIdForUpdate(300L);
        ordered.verify(dinnerAssignmentRepository).delete(assignment);
    }

    @Test
    void capacityWriteTransactionsUseReadCommittedIsolation() throws NoSuchMethodException {
        assertReadCommitted("createTable", DinnerTableRequest.class);
        assertReadCommitted("updateTable", Long.class, DinnerTableRequest.class);
        assertReadCommitted("deleteTable", Long.class, Long.class);
        assertReadCommitted("assign", DinnerAssignRequest.class);
        assertReadCommitted("clear", Long.class);
    }

    @Test
    void queryOnlyReturnsApprovedAndAssignedAttendees() {
        Attendee approved = attendee(11L, "张三", "13800000000", "示例公司", ReviewStatus.APPROVED);
        DinnerAssignment assignment = assignment(200L, approved, "3号桌", null, null, "主桌");
        when(dinnerAssignmentRepository.findByMeetingIdAndAttendeeId(1L, 11L)).thenReturn(Optional.of(assignment));

        DinnerQueryResult result = dinnerService.query(1L, approved);
        assertThat(result.attendeeName()).isEqualTo("张三");
        assertThat(result.tableNo()).isEqualTo("3号桌");
        assertThat(result.venue()).isEqualTo("三层宴会厅");
        assertThat(result.dinnerTime()).isEqualTo("18:30");

        Attendee pending = attendee(12L, "李四", "13900000000", "示例公司", ReviewStatus.PENDING);
        assertThatThrownBy(() -> dinnerService.query(1L, pending))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核通过");

        Attendee unassigned = attendee(13L, "王五", "13700000000", "示例公司", ReviewStatus.APPROVED);
        when(dinnerAssignmentRepository.findByMeetingIdAndAttendeeId(1L, 13L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> dinnerService.query(1L, unassigned))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未安排晚宴桌号");
    }

    private Attendee attendee(Long id, String name, String phone, String organization, ReviewStatus reviewStatus) {
        Attendee attendee = new Attendee();
        attendee.setId(id);
        attendee.setMeeting(meeting);
        attendee.setName(name);
        attendee.setPhone(phone);
        attendee.setOrganization(organization);
        attendee.setTitle("职务");
        attendee.setAttendeeType(AttendeeType.NORMAL);
        attendee.setReviewStatus(reviewStatus);
        attendee.setAttendeeCode("FORUM-" + id);
        return attendee;
    }

    private void assertReadCommitted(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Transactional transactional = DinnerService.class.getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    private DinnerAssignment assignment(Long id,
                                        Attendee attendee,
                                        String tableNo,
                                        String venue,
                                        String dinnerTime,
                                        String remark) {
        DinnerAssignment assignment = new DinnerAssignment();
        assignment.setId(id);
        assignment.setMeeting(meeting);
        assignment.setAttendee(attendee);
        assignment.setTableNo(tableNo);
        assignment.setVenue(venue);
        assignment.setDinnerTime(dinnerTime);
        assignment.setRemark(remark);
        return assignment;
    }

    private DinnerTable table(Long id, String tableNo, Integer capacity) {
        DinnerTable table = new DinnerTable();
        table.setId(id);
        table.setMeeting(meeting);
        table.setTableNo(tableNo);
        table.setCapacity(capacity);
        return table;
    }

    private MockMultipartFile workbook(List<String> headers, List<String>... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("晚宴分配");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows[r];
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "dinner.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("测试 XLSX 生成失败", ex);
        }
    }
}
