package com.regivolley.api.infrastructure.security;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Bounds request bodies at 64 KiB (threat model section 7, "Size limits"), in two layers:
 * <ul>
 *   <li>before anything reads: 413 when the declared Content-Length is too large, 411 when the body has no declared length
 *       (chunked). The PWA sends JSON with a Content-Length, so nothing legitimate is lost;</li>
 *   <li>while reading: the request is wrapped so that the 64 KiB-and-first byte actually read throws
 *       {@link MaxUploadSizeExceededException} (413 through the exception advice), so a body that was not caught by the
 *       headers, or lies about them, is still bounded.</li>
 * </ul>
 */
class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 64 * 1024;

    private final ApiErrorWriter writer;

    RequestSizeLimitFilter(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > MAX_BODY_BYTES) {
            writer.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ApiError.ofStatus(413, RequestIds.current()));
            return;
        }
        if (length < 0 && request.getHeader("Transfer-Encoding") != null) {
            writer.write(response, HttpServletResponse.SC_LENGTH_REQUIRED, ApiError.ofStatus(411, RequestIds.current()));
            return;
        }
        chain.doFilter(new BoundedRequest(request), response);
    }

    /** The request whose body can be read only up to {@link #MAX_BODY_BYTES}. */
    private static final class BoundedRequest extends HttpServletRequestWrapper {

        private ServletInputStream stream;

        BoundedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new BoundedInputStream(super.getInputStream());
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class BoundedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long read;

        BoundedInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int next = delegate.read();
            if (next >= 0) {
                count(1);
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int bytes) {
            read += bytes;
            if (read > MAX_BODY_BYTES) {
                throw new MaxUploadSizeExceededException(MAX_BODY_BYTES);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
