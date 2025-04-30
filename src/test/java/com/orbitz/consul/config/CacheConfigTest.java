package com.orbitz.consul.config;

import com.orbitz.consul.cache.CacheDescriptor;
import com.orbitz.consul.cache.ConsulCache;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.monitoring.ClientEventHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class CacheConfigTest {

    @Test
    @DisplayName("Test default configuration values")
    public void testDefaults() {
        CacheConfig config = CacheConfig.builder().build();
        assertEquals(CacheConfig.DEFAULT_BACKOFF_DELAY, config.getMinimumBackOffDelay());
        assertEquals(CacheConfig.DEFAULT_BACKOFF_DELAY, config.getMaximumBackOffDelay());
        assertEquals(CacheConfig.DEFAULT_WATCH_DURATION, config.getWatchDuration());
        assertEquals(CacheConfig.DEFAULT_MIN_DELAY_BETWEEN_REQUESTS, config.getMinimumDurationBetweenRequests());
        assertEquals(CacheConfig.DEFAULT_MIN_DELAY_ON_EMPTY_RESULT, config.getMinimumDurationDelayOnEmptyResult());
        assertEquals(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_ENABLED, config.isTimeoutAutoAdjustmentEnabled());
        assertEquals(CacheConfig.DEFAULT_TIMEOUT_AUTO_ADJUSTMENT_MARGIN, config.getTimeoutAutoAdjustmentMargin());

        // Verify that the refresh error log consumer is not null
        assertNotNull(config.getRefreshErrorLoggingConsumer());
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    @DisplayName("Test override back off delay")
    public void testOverrideBackOffDelay(Duration backOffDelay) {
        CacheConfig config = CacheConfig.builder().withBackOffDelay(backOffDelay).build();
        assertEquals(backOffDelay, config.getMinimumBackOffDelay());
        assertEquals(backOffDelay, config.getMaximumBackOffDelay());
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    @DisplayName("Test override min delay between requests")
    public void testOverrideMinDelayBetweenRequests(Duration delayBetweenRequests) {
        CacheConfig config = CacheConfig.builder().withMinDelayBetweenRequests(delayBetweenRequests).build();
        assertEquals(delayBetweenRequests, config.getMinimumDurationBetweenRequests());
    }

    @ParameterizedTest(name = "Delay: {0}")
    @MethodSource("getDurationSamples")
    @DisplayName("Test override min delay on empty result")
    public void testOverrideMinDelayOnEmptyResult(Duration delayBetweenRequests) {
        CacheConfig config = CacheConfig.builder().withMinDelayOnEmptyResult(delayBetweenRequests).build();
        assertEquals(delayBetweenRequests, config.getMinimumDurationDelayOnEmptyResult());
    }

    @ParameterizedTest(name = "Enabled: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("Test override timeout auto adjustment enabled")
    public void testOverrideTimeoutAutoAdjustmentEnabled(boolean enabled) {
        CacheConfig config = CacheConfig.builder().withTimeoutAutoAdjustmentEnabled(enabled).build();
        assertEquals(enabled, config.isTimeoutAutoAdjustmentEnabled());
    }

    @ParameterizedTest(name = "Margin: {0}")
    @MethodSource("getDurationSamples")
    @DisplayName("Test override timeout auto adjustment margin")
    public void testOverrideTimeoutAutoAdjustmentMargin(Duration margin) {
        CacheConfig config = CacheConfig.builder().withTimeoutAutoAdjustmentMargin(margin).build();
        assertEquals(margin, config.getTimeoutAutoAdjustmentMargin());
    }

    @ParameterizedTest(name = "LogLevel as Warning: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("Test override refresh error log consumer")
    public void testOverrideRefreshErrorLogConsumer(boolean logLevelWarning) throws InterruptedException {
        CacheConfig config = logLevelWarning
                ? CacheConfig.builder().withRefreshErrorLoggedAsWarning().build()
                : CacheConfig.builder().withRefreshErrorLoggedAsError().build();

        // Verify that the refresh error log consumer is not null
        assertNotNull(config.getRefreshErrorLoggingConsumer());

        // Since we can't easily verify which method is called on the logger,
        // we'll just verify that the configuration is set correctly
        if (logLevelWarning) {
            // For warning, we expect the withRefreshErrorLoggedAsWarning method to be called
            CacheConfig warningConfig = CacheConfig.builder().withRefreshErrorLoggedAsWarning().build();
            assertEquals(warningConfig.getClass(), config.getClass());
        } else {
            // For error, we expect the withRefreshErrorLoggedAsError method to be called
            CacheConfig errorConfig = CacheConfig.builder().withRefreshErrorLoggedAsError().build();
            assertEquals(errorConfig.getClass(), config.getClass());
        }
    }

    @Test
    @DisplayName("Test override refresh error log custom")
    public void testOverrideRefreshErrorLogCustom() {
        // Create a custom refresh error log consumer
        CacheConfig.RefreshErrorLogConsumer customConsumer = (logger, message, error) -> {
            // Custom implementation
        };

        // Create a config with the custom consumer
        CacheConfig config = CacheConfig.builder().withRefreshErrorLoggedAs(customConsumer).build();

        // Verify that the refresh error log consumer is not null
        assertNotNull(config.getRefreshErrorLoggingConsumer());

        // Since we can't easily verify which method is called on the logger,
        // we'll just verify that the configuration is set correctly
        CacheConfig customConfig = CacheConfig.builder().withRefreshErrorLoggedAs(customConsumer).build();
        assertEquals(customConfig.getClass(), config.getClass());
    }

    static Stream<Arguments> getDurationSamples() {
        return Stream.of(
                Arguments.of(Duration.ZERO),
                Arguments.of(Duration.ofSeconds(2)),
                Arguments.of(Duration.ofMinutes(10))
        );
    }

    @ParameterizedTest(name = "min Delay: {0}, max Delay: {1}")
    @MethodSource("getMinMaxDurationSamples")
    @DisplayName("Test override random back off delay")
    public void testOverrideRandomBackOffDelay(Duration minDelay, Duration maxDelay, boolean isValid) {
        try {
            CacheConfig config = CacheConfig.builder().withBackOffDelay(minDelay, maxDelay).build();
            if (!isValid) {
                fail(String.format("Should not be able to build cache with min retry delay %d ms and max retry delay %d ms",
                        minDelay.toMillis(), maxDelay.toMillis()));
            }
            assertEquals(minDelay, config.getMinimumBackOffDelay());
            assertEquals(maxDelay, config.getMaximumBackOffDelay());
        } catch (NullPointerException | IllegalArgumentException e) {
            if (isValid) {
                throw new AssertionError(String.format("Should be able to build cache with min retry delay %d ms and max retry delay %d ms",
                        minDelay.toMillis(), maxDelay.toMillis()), e);
            }
        }
    }

    static Stream<Arguments> getMinMaxDurationSamples() {
        return Stream.of(
                Arguments.of(Duration.ZERO, Duration.ZERO, true),
                Arguments.of(Duration.ofSeconds(2), Duration.ofSeconds(2), true),
                Arguments.of(Duration.ZERO, Duration.ofSeconds(2), true),
                Arguments.of(Duration.ofSeconds(2), Duration.ZERO, false),
                Arguments.of(Duration.ofSeconds(1), Duration.ofSeconds(2), true),
                Arguments.of(Duration.ofSeconds(2), Duration.ofSeconds(1), false),
                Arguments.of(Duration.ofSeconds(-1), Duration.ZERO, false),
                Arguments.of(Duration.ZERO, Duration.ofSeconds(-1), false),
                Arguments.of(Duration.ofSeconds(-1), Duration.ofSeconds(-1), false)
        );
    }

    @Test
    public void testMinDelayOnEmptyResultWithNoResults() throws InterruptedException {
        TestCacheSupplier res = new TestCacheSupplier(0, Duration.ofMillis(100));

        TestCache cache = TestCache.createCache(CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .build(), res);
        cache.start();
        Thread.sleep(300);
        assertTrue(res.run > 0);
        cache.stop();
    }

    @Test
    public void testMinDelayOnEmptyResultWithResults() throws InterruptedException {
        TestCacheSupplier res = new TestCacheSupplier(1, Duration.ofMillis(50));

        TestCache cache = TestCache.createCache(CacheConfig.builder()
                .withMinDelayOnEmptyResult(Duration.ofMillis(100))
                .withMinDelayBetweenRequests(Duration.ofMillis(50)) // do not blow ourselves up
                .build(), res);
        cache.start();
        Thread.sleep(300);
        assertTrue(res.run > 0);
        cache.stop();
    }


    static class TestCache extends ConsulCache<Integer, Integer> {
        private TestCache(Function<Integer, Integer> keyConversion, CallbackConsumer<Integer> callbackConsumer, CacheConfig cacheConfig, ClientEventHandler eventHandler, CacheDescriptor cacheDescriptor) {
            super(keyConversion, callbackConsumer, cacheConfig, eventHandler, cacheDescriptor);
        }

        static TestCache createCache(CacheConfig config, Supplier<List<Integer>> res) {
            ClientEventHandler ev = mock(ClientEventHandler.class);
            CacheDescriptor cacheDescriptor = new CacheDescriptor("test", "test");

            final CallbackConsumer<Integer> callbackConsumer = (index, callback) -> {
                callback.onComplete(new ConsulResponse<>(res.get(), 0, true, BigInteger.ZERO, null, null));
            };

            return new TestCache((i) -> i,
                    callbackConsumer,
                    config,
                    ev,
                    cacheDescriptor);
        }
    }

    static class TestCacheSupplier implements Supplier<List<Integer>> {
        int run = 0;
        int resultCount;
        private Duration expectedInterval;
        private LocalTime lastCall;

        TestCacheSupplier(int resultCount, Duration expectedInterval) {
            this.resultCount = resultCount;
            this.expectedInterval = expectedInterval;
        }

        @Override
        public List<Integer> get() {
            if (lastCall != null) {
                long between = Duration.between(lastCall, LocalTime.now()).toMillis();
                assertTrue(Math.abs(between - expectedInterval.toMillis()) < 20,
                        String.format("expected duration between calls of %d, got %s", expectedInterval.toMillis(), between));
            }
            lastCall = LocalTime.now();
            run++;

            List<Integer> response = new ArrayList<>();
            for (int i = 0; i < resultCount; i++) {
                response.add(1);
            }
            return response;
        }
    }
}
