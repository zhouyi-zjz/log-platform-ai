package com.zhouyi.admin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 在 JSON 反序列化前限制采集请求体，避免仅在 Controller 序列化后检查导致大请求先进入内存。
 */
@Component
public class LogIngestRequestFilter extends OncePerRequestFilter {

    private static final String INGEST_PATH = "/monitor/api/logs/ingest";
    private final LogIngestProperties properties;

    public LogIngestRequestFilter(LogIngestProperties properties) {
        this.properties = properties;
    }

    /** 只包装采集入口，其他 API 保持 Spring 默认请求读取行为。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {
        if (!INGEST_PATH.equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        long max = Math.max(1, properties.getMaxPayloadBytes());
        long declared = request.getContentLengthLong();
        if (declared > max) {
            response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "日志消息超过大小限制");
            return;
        }
        try {
            filterChain.doFilter(new LimitedRequestWrapper(request, max), response);
        } catch (IOException | jakarta.servlet.ServletException exception) {
            if (!containsPayloadLimit(exception)) {
                throw exception;
            }
            if (!response.isCommitted()) {
                response.sendError(HttpStatus.PAYLOAD_TOO_LARGE.value(), "日志消息超过大小限制");
            }
        }
    }

    /** Spring MVC 可能将输入流异常包装成 HttpMessageNotReadableException，需要沿 cause 链识别。 */
    private static boolean containsPayloadLimit(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PayloadTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 将读取上限施加到 Servlet 输入流，覆盖 chunked 请求没有 Content-Length 的场景。 */
    private static final class LimitedRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;

        private LimitedRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        /** 同时覆盖 Reader 读取路径，避免容器按字符流读取时绕过字节上限。 */
        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    /** 读取超过上限时立即中断请求，不缓存整个请求体。 */
    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long readBytes;

        private LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && ++readBytes > maxBytes) {
                throw new PayloadTooLargeException();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int value = delegate.read(buffer, offset, length);
            if (value > 0 && (readBytes += value) > maxBytes) {
                throw new PayloadTooLargeException();
            }
            return value;
        }

        @Override
        public boolean isFinished() { return delegate.isFinished(); }

        @Override
        public boolean isReady() { return delegate.isReady(); }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener listener) { delegate.setReadListener(listener); }
    }

    private static final class PayloadTooLargeException extends IOException {
    }
}
