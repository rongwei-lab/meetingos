package com.meetingos.checkin;

import com.meetingos.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class CheckinCodeParser {

    public CheckinCodePayload parse(Long requestMeetingId, String qrCode, String attendeeCode) {
        if (attendeeCode != null && !attendeeCode.isBlank()) {
            return new CheckinCodePayload(requestMeetingId, attendeeCode.trim());
        }
        if (qrCode == null || qrCode.isBlank()) {
            throw BusinessException.badRequest("请提供二维码内容或参会码");
        }

        String text = qrCode.trim();
        if (text.startsWith("MEETINGOS:") || text.startsWith("MEETINGOS-CHECKIN:")) {
            // 线下二维码建议内容：MEETINGOS-CHECKIN:{meetingId}:{attendeeCode}，解析稳定，不依赖域名变化。
            String[] parts = text.split(":", 3);
            if (parts.length != 3) {
                throw BusinessException.badRequest("签到码格式不正确");
            }
            return new CheckinCodePayload(Long.valueOf(parts[1]), parts[2]);
        }
        if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("/")) {
            try {
                // 公开签到页可能以完整 URL 或站内相对路径形式进入扫码枪。
                // 相对路径没有 scheme/host，补一个本地域名后复用同一套查询参数解析逻辑。
                URI uri = text.startsWith("/") ? URI.create("http://meetingos.local" + text) : URI.create(text);
                var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
                String code = params.getFirst("code");
                if (code == null) {
                    code = params.getFirst("attendeeCode");
                }
                if (code == null) {
                    code = params.getFirst("attendeeId");
                }
                String meeting = params.getFirst("meetingId");
                if (code == null || meeting == null) {
                    throw BusinessException.badRequest("签到链接缺少 code 或 meetingId");
                }
                return new CheckinCodePayload(Long.valueOf(meeting), code);
            } catch (IllegalArgumentException ex) {
                throw BusinessException.badRequest("签到链接格式不正确");
            }
        }
        return new CheckinCodePayload(requestMeetingId, text);
    }
}
