package com.meetingos.common;

/**
 * 导入失败行明细。
 *
 * @param rowNumber .xlsx 模板中的原始行号，便于会务人员回到模板定位问题
 * @param reason    面向业务人员的失败原因
 */
public record ImportFailure(
        int rowNumber,
        String reason
) {
}
