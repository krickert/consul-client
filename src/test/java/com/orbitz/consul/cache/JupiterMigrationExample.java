package com.orbitz.consul.cache;

import com.google.common.collect.ImmutableMap;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.ImmutableValue;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.monitoring.ClientEventHandler;
import com.orbitz.consul.option.ConsistencyMode;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * This is an example of how to migrate from JUnit 4 to JUnit Jupiter (JUnit 5).
 * It demonstrates the key differences and migration patterns.
 */
public class JupiterMigrationExample {

    /**
     * Test that if Consul for some reason returns a duplicate service or keyvalue entry
     * that we recover gracefully by taking the first value, ignoring duplicates, and warning
     * user of the condition
     */
    @Test
    @DisplayName("Test duplicate services don't cause failure")
    public void testDuplicateServicesDontCauseFailure() {
        final Function<Value, String> keyExtractor = input -> "SAME_KEY";
        final List<Value> response = Arrays.asList(mock(Value.class), mock(Value.class));
        CacheConfig cacheConfig = mock(CacheConfig.class);
        ClientEventHandler eventHandler = mock(ClientEventHandler.class);

        final StubCallbackConsumer callbackConsumer = new StubCallbackConsumer(Collections.emptyList());

        final ConsulCache<String, Value> consulCache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig, eventHandler, new CacheDescriptor(""));
        final ConsulResponse<List<Value>> consulResponse = new ConsulResponse<>(response, 0, false, BigInteger.ONE, null, null);
        final ImmutableMap<String, Value> map = consulCache.convertToMap(consulResponse);
        assertNotNull(map);
        // Second copy has been weeded out
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("Test watch params with no additional options")
    public void testWatchParamsWithNoAdditionalOptions() {
        BigInteger index = new BigInteger("12");
        QueryOptions expectedOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();
        QueryOptions actualOptions = ConsulCache.watchParams(index, 10, QueryOptions.BLANK);
        assertEquals(expectedOptions, actualOptions);
    }

    @Test
    @DisplayName("Test watch params with additional options")
    public void testWatchParamsWithAdditionalOptions() {
        BigInteger index = new BigInteger("12");
        QueryOptions additionalOptions = ImmutableQueryOptions.builder()
                .consistencyMode(ConsistencyMode.STALE)
                .addTag("someTag")
                .token("186596")
                .near("156892")
                .build();

        QueryOptions expectedOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .consistencyMode(ConsistencyMode.STALE)
                .addTag("someTag")
                .token("186596")
                .near("156892")
                .build();

        QueryOptions actualOptions = ConsulCache.watchParams(index, 10, additionalOptions);
        assertEquals(expectedOptions, actualOptions);
    }

    /**
     * Example of migrating a test with expected exception.
     * In JUnit 4: @Test(expected = IllegalArgumentException.class)
     * In JUnit 5: Use assertThrows
     */
    @Test
    @DisplayName("Test watch params with additional index and waiting throws")
    public void testWatchParamsWithAdditionalIndexAndWaitingThrows() {
        BigInteger index = new BigInteger("12");
        QueryOptions additionalOptions = ImmutableQueryOptions.builder()
                .index(index)
                .wait("10s")
                .build();
                
        assertThrows(IllegalArgumentException.class, () -> {
            ConsulCache.watchParams(index, 10, additionalOptions);
        });
    }

    /**
     * Example of migrating a parameterized test.
     * In JUnit 4: @Parameters(method = "getRetryDurationSamples")
     * In JUnit 5: @ParameterizedTest + @MethodSource
     */
    @ParameterizedTest(name = "min Delay: {0}, max Delay: {1}")
    @MethodSource("getRetryDurationSamples")
    @DisplayName("Test retry duration")
    public void testRetryDuration(Duration minDelay, Duration maxDelay) {
        CacheConfig cacheConfig = CacheConfig.builder().withBackOffDelay(minDelay, maxDelay).build();
        for (int i=0; i < 1000; i++) {
            long retryDurationMs = ConsulCache.computeBackOffDelayMs(cacheConfig);
            String message = String.format("Retry duration expected between %s and %s but got %d ms", minDelay, maxDelay, retryDurationMs);
            assertTrue(retryDurationMs >= minDelay.toMillis(), message);
            assertTrue(retryDurationMs <= maxDelay.toMillis(), message);
        }
    }

    /**
     * Example of migrating a parameterized test data provider.
     * In JUnit 4: Return Object[] or Object[][]
     * In JUnit 5: Return Stream<Arguments>
     */
    static Stream<Arguments> getRetryDurationSamples() {
        return Stream.of(
            // Same duration
            Arguments.of(Duration.ZERO, Duration.ZERO),
            Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(10)),
            // Different durations
            Arguments.of(Duration.ofSeconds(10), Duration.ofSeconds(11)),
            Arguments.of(Duration.ofMillis(10), Duration.ofMinutes(1))
        );
    }

    @Test
    @DisplayName("Test listener is called")
    public void testListenerIsCalled() {
        final Function<Value, String> keyExtractor = Value::getKey;
        final CacheConfig cacheConfig = CacheConfig.builder().build();
        ClientEventHandler eventHandler = mock(ClientEventHandler.class);

        final String key = "foo";
        final ImmutableValue value = ImmutableValue.builder()
                .createIndex(1)
                .modifyIndex(2)
                .lockIndex(2)
                .key(key)
                .flags(0)
                .build();
        final List<Value> result = Collections.singletonList(value);
        final StubCallbackConsumer callbackConsumer = new StubCallbackConsumer(
                result);

        final ConsulCache<String, Value> cache = new ConsulCache<>(keyExtractor, callbackConsumer, cacheConfig,
                eventHandler, new CacheDescriptor(""));
        try {
            final StubListener listener = new StubListener();

            cache.addListener(listener);
            cache.start();

            assertEquals(1, listener.getCallCount());
            assertEquals(1, callbackConsumer.getCallCount());

            final Map<String, Value> lastValues = listener.getLastValues();
            assertNotNull(lastValues);
            assertEquals(result.size(), lastValues.size());
            assertTrue(lastValues.containsKey(key));
            assertEquals(value, lastValues.get(key));
        } finally {
            cache.stop();
        }
    }
}