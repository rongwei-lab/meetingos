package com.meetingos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingos.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 在 Jackson 反序列化前限制 JSON 请求体，避免大请求先占用大量堆内存和 CPU。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonBodySizeLimitFilter extends OncePerRequestFilter {

    private static final long ABSOLUTE_MAX_BYTES = 16L * 1024 * 1024;
    private static final Set<String> RAW_BODY_LIMIT_PATHS = Set.of(
            "/api/tduck/webhook",
            "/api/public/tduck/webhook",
            "/api/integrations/tduck/webhook"
    );

    private final ObjectMapper objectMapper;
    private final long maxJsonBodyBytes;

    public JsonBodySizeLimitFilter(ObjectMapper objectMapper,
                                   @Value("${meetingos.http.max-json-body-bytes:1048576}") long maxJsonBodyBytes) {
        this.objectMapper = objectMapper;
        this.maxJsonBodyBytes = Math.max(1, Math.min(maxJsonBodyBytes, ABSOLUTE_MAX_BYTES));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            return true;
        }
        // Webhook 控制器接收原始字符串并在验签后解析，TDuck 兼容回调可能使用 text/plain。
        // 这三条公网入口必须无视 Content-Type 先执行相同的字节上限，防止绕过 JSON 过滤。
        if (RAW_BODY_LIMIT_PATHS.contains(request.getRequestURI())) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null) {
            return true;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return !(MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json"));
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxJsonBodyBytes) {
            reject(response);
            return;
        }

        byte[] body = readWithinLimit(request, response);
        if (body == null) {
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private byte[] readWithinLimit(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int initialSize = (int) Math.min(maxJsonBodyBytes, 8192);
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize);
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = request.getInputStream().read(buffer)) != -1) {
            total += read;
            if (total > maxJsonBodyBytes) {
                reject(response);
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.fail("PAYLOAD_TOO_LARGE", "JSON 请求体不能超过 " + maxJsonBodyBytes + " 字节")
        );
        response.flushBuffer();
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 请求体已经完整缓存在内存中，不需要异步读取回调。
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
