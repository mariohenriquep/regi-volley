package com.regivolley.api.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The body bound of threat model section 7, at the level of the filter: declared length, unknown length and bytes actually read. */
class RequestSizeLimitFilterTest {

    private static final int MAX = 64 * 1024;

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new ApiErrorWriter(JsonMapper.builder().build()));

    /** A request that declares no length at all, whatever its body: how a chunked or understated body reaches the filter. */
    private static MockHttpServletRequest undeclaredLength(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }

    private static byte[] bytes(int size) {
        return new byte[size];
    }

    @Test
    void refusesADeclaredLengthOverTheLimitWithoutReadingAnything() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
        request.setContent(bytes(MAX + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<HttpServletRequest> reached = new AtomicReference<>();

        // Act
        filter.doFilter(request, response, (req, res) -> reached.set((HttpServletRequest) req));

        // Assert
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reached.get()).isNull();
    }

    @Test
    void refusesABodyOfUnknownLengthThatIsChunked() throws Exception {
        // Arrange
        MockHttpServletRequest request = undeclaredLength(bytes(10));
        request.addHeader("Transfer-Encoding", "chunked");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, (req, res) -> { });

        // Assert
        assertThat(response.getStatus()).isEqualTo(411);
    }

    @Test
    void letsABodyWithinTheLimitThroughAndReadable() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
        request.setContent("hello".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> read = new AtomicReference<>();

        // Act
        filter.doFilter(request, response, (req, res) -> read.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        // Assert
        assertThat(read.get()).isEqualTo("hello");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void cutsABodyWithoutADeclaredLengthAfterTheLimitActuallyRead() {
        // Arrange - nothing in the headers gives it away: only counting the bytes can
        MockHttpServletRequest request = undeclaredLength(bytes(MAX + 1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Executable act = () -> filter.doFilter(request, response, (req, res) -> {
            try (InputStream in = req.getInputStream()) {
                in.readAllBytes();
            }
        });

        // Act
        MaxUploadSizeExceededException e = assertThrows(MaxUploadSizeExceededException.class, act);

        // Assert
        assertThat(e.getMaxUploadSize()).isEqualTo(MAX);
    }

    @Test
    void letsAnUndeclaredBodyOfExactlyTheLimitThrough() throws Exception {
        // Arrange
        MockHttpServletRequest request = undeclaredLength(bytes(MAX));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Integer> size = new AtomicReference<>();

        // Act
        filter.doFilter(request, response, (req, res) -> size.set(req.getInputStream().readAllBytes().length));

        // Assert
        assertThat(size.get()).isEqualTo(MAX);
    }

    @Test
    void boundsTheReaderTooAndCountsSingleByteReads() {
        // Arrange
        MockHttpServletRequest request = undeclaredLength(bytes(MAX + 1));
        request.setCharacterEncoding("UTF-8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Executable viaReader = () -> filter.doFilter(request, response, (req, res) -> req.getReader().transferTo(Writer.nullWriter()));
        MockHttpServletRequest second = undeclaredLength(bytes(MAX + 1));
        Executable singleBytes = () -> filter.doFilter(second, new MockHttpServletResponse(), (req, res) -> {
            InputStream in = req.getInputStream();
            while (in.read() >= 0) {
                // drain
            }
        });

        // Act
        MaxUploadSizeExceededException viaReaderFailure = assertThrows(MaxUploadSizeExceededException.class, viaReader);
        MaxUploadSizeExceededException singleByteFailure = assertThrows(MaxUploadSizeExceededException.class, singleBytes);

        // Assert
        assertThat(viaReaderFailure.getMaxUploadSize()).isEqualTo(MAX);
        assertThat(singleByteFailure.getMaxUploadSize()).isEqualTo(MAX);
    }

    @Test
    void aBodyReadTwiceIsStillOneStream() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
        request.setContent("abc".getBytes(StandardCharsets.UTF_8));
        AtomicReference<Boolean> same = new AtomicReference<>();

        // Act
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> same.set(req.getInputStream() == req.getInputStream()));

        // Assert
        assertThat(same.get()).isTrue();
    }
}
