package com.meetingos.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 统一解析并规范化请求客户端 IP。
 *
 * <p>转发头只有在部署方显式声明反向代理可信时才会参与解析；无效或异常值不会进入
 * 限流 key 和审计日志，而是回退到 Servlet 容器看到的对端地址。</p>
 */
@Component
public class ClientIpResolver {

    public static final String UNKNOWN_CLIENT = "unknown";

    private static final int MAX_IP_LITERAL_LENGTH = 45;
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9a-fA-F:]+$");

    private final PublicQueryProperties properties;

    public ClientIpResolver(PublicQueryProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_CLIENT;
        }
        if (properties.isTrustForwardHeaders()) {
            String forwarded = firstForwardedAddress(request.getHeader("X-Forwarded-For"));
            String normalizedForwarded = normalizeIpLiteral(forwarded);
            if (normalizedForwarded != null) {
                return normalizedForwarded;
            }

            String normalizedRealIp = normalizeIpLiteral(request.getHeader("X-Real-IP"));
            if (normalizedRealIp != null) {
                return normalizedRealIp;
            }
        }

        String normalizedRemoteAddress = normalizeIpLiteral(request.getRemoteAddr());
        return normalizedRemoteAddress == null ? UNKNOWN_CLIENT : normalizedRemoteAddress;
    }

    private String firstForwardedAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        int comma = forwardedFor.indexOf(',');
        return comma < 0 ? forwardedFor : forwardedFor.substring(0, comma);
    }

    private String normalizeIpLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank() || value.length() > MAX_IP_LITERAL_LENGTH) {
            return null;
        }
        String ipv4 = normalizeIpv4(value);
        if (ipv4 != null) {
            return ipv4;
        }
        if (!value.contains(":") || !IPV6_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address ? address.getHostAddress() : null;
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private String normalizeIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (octet > 255) {
                return null;
            }
            if (!normalized.isEmpty()) {
                normalized.append('.');
            }
            normalized.append(octet);
        }
        return normalized.toString();
    }
}
