package com.orbitz.consul.cache;

import com.orbitz.consul.config.CacheConfig;
import okhttp3.Interceptor;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public class TimeoutInterceptorTest {

    @ParameterizedTest(name = "expected timeout of {4} ms for url {0} with timeout of {1} ms and margin of {3} ms (enabled: {2})")
    @MethodSource("getInterceptParameters")
    @DisplayName("Check intercept")
    public void checkIntercept(String url, int defaultTimeout, boolean enabled, int margin, int expectedTimeoutMs)
            throws IOException {
        CacheConfig config = createConfigMock(enabled, margin);
        Interceptor.Chain chain = createChainMock(defaultTimeout, url);

        TimeoutInterceptor interceptor = new TimeoutInterceptor(config);
        interceptor.intercept(chain);
        verify(chain).withReadTimeout(eq(expectedTimeoutMs), eq(TimeUnit.MILLISECONDS));
    }

    static Stream<Arguments> getInterceptParameters() {
        return Stream.of(
                // Auto Adjustment disabled
                Arguments.of("http://my_call", 1, false, 0, 1),
                // Auto Adjustment disabled and valid "wait" query parameter
                Arguments.of("http://my_call?wait=1s", 1, false, 0, 1),
                // Auto Adjustment enabled but not "wait" query parameter
                Arguments.of("http://my_call", 1, true, 0, 1),
                Arguments.of("http://my_call", 1, true, 2, 1),
                // Auto Adjustment enabled but invalid "wait" query parameter
                Arguments.of("http://my_call?wait=1", 1, true, 0, 1),
                Arguments.of("http://my_call?wait=3h", 1, true, 2, 1),
                // Auto Adjustment enabled and valid "wait" query parameter
                // Note: ceil(1/16*1000) = 63 and ceil(1/16*60000)=3750
                Arguments.of("http://my_call?wait=1s", 1, true, 0, 1063),
                Arguments.of("http://my_call?wait=1s", 0, true, 2, 1065),
                Arguments.of("http://my_call?wait=1s", 1, true, 2, 1065),
                Arguments.of("http://my_call?wait=1m", 1, true, 2, 63752)
        );
    }

    private CacheConfig createConfigMock(boolean autoAdjustEnabled, int autoAdjustMargin) {
        CacheConfig config = mock(CacheConfig.class);
        when(config.isTimeoutAutoAdjustmentEnabled()).thenReturn(autoAdjustEnabled);
        when(config.getTimeoutAutoAdjustmentMargin()).thenReturn(Duration.ofMillis(autoAdjustMargin));
        return config;
    }

    private Interceptor.Chain createChainMock(int defaultTimeout, String url) throws IOException {
        Request request = new Request.Builder().url(url).build();

        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.readTimeoutMillis()).thenReturn(defaultTimeout);
        doReturn(chain).when(chain).withReadTimeout(anyInt(), any(TimeUnit.class));
        doReturn(null).when(chain).proceed(any(Request.class));

        return chain;
    }
}
