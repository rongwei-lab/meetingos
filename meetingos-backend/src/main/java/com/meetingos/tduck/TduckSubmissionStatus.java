package com.meetingos.tduck;

/**
 * 一名参会人可能在 TDuck 多次提交。主参会人资料只保留当前采用的一次，
 * 其余 dataId 的处理结果在这里显式保存，避免后续全量同步重复报错。
 */
public enum TduckSubmissionStatus {
    ACTIVE,
    SUPERSEDED,
    IGNORED,
    PENDING_CONFLICT,
    CANCELLED
}
