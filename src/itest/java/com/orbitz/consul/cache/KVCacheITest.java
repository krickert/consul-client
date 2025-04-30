package com.orbitz.consul.cache;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.Synchroniser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class KVCacheITest extends BaseIntegrationTest {

    Consul consulClient;
    @BeforeEach
    public void before() {
        consulClient = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withClientConfiguration(new ClientConfig(CacheConfig.builder().withWatchDuration(Duration.ofSeconds(1)).build()))
                .withReadTimeoutMillis(Duration.ofSeconds(11).toMillis())
                .withConnectTimeoutMillis(Duration.ofMillis(500).toMillis())
                .withWriteTimeoutMillis(Duration.ofMillis(500).toMillis())
                .build();
    }

    @Test
    @DisplayName("Test node cache KV")
    public void nodeCacheKvTest() throws Exception {

        KeyValueClient kvClient = consulClient.keyValueClient();
        String root = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            kvClient.putValue(root + "/" + i, String.valueOf(i));
        }

        KVCache nc = KVCache.newCache(
                kvClient, root, 10
        );
        nc.start();

        if (!nc.awaitInitialized(1, TimeUnit.SECONDS)) {
            fail("cache initialization failed");
        }

        ImmutableMap<String, Value> map = nc.getMap();
        for (int i = 0; i < 5; i++) {
            String keyStr = String.format("%s/%s", root, i);
            String valStr = String.valueOf(i);
            assertEquals(valStr, map.get(keyStr).getValueAsString().get());
        }

        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) {
                kvClient.putValue(root + "/" + i, String.valueOf(i * 10));
            }
        }

        Synchroniser.pause(Duration.ofMillis(100));

        map = nc.getMap();
        for (int i = 0; i < 5; i++) {
            String keyStr = String.format("%s/%s", root, i);
            String valStr = i % 2 == 0 ? "" + (i * 10) : String.valueOf(i);
            assertEquals(valStr, map.get(keyStr).getValueAsString().get());
        }

        kvClient.deleteKeys(root);

    }

    @Test
    @DisplayName("Test listeners")
    public void testListeners() throws Exception {
        KeyValueClient kvClient = consulClient.keyValueClient();
        String root = UUID.randomUUID().toString();
        final List<Map<String, Value>> events = new ArrayList<>();

        try (KVCache nc = KVCache.newCache(kvClient, root, 10)) {
            nc.addListener(events::add);
            nc.start();

            if (!nc.awaitInitialized(1, TimeUnit.SECONDS)) {
                fail("cache initialization failed");
            }

            for (int keyIdx = 0; keyIdx < 5; keyIdx++) {
                kvClient.putValue(String.format("%s/%s", root, keyIdx), String.valueOf(keyIdx));
                Synchroniser.pause(Duration.ofMillis(100));
            }
        }

        assertEquals(6, events.size());
        for (int eventIdx = 1; eventIdx < 6; eventIdx++) {
            Map<String, Value> map = events.get(eventIdx);
            assertEquals(eventIdx, map.size());

            for (int keyIdx = 0; keyIdx < eventIdx; keyIdx++) {
                Optional<String> value = map
                        .get(String.format("%s/%s", root, keyIdx))
                        .getValueAsString();

                if (!value.isPresent()) {
                    fail(String.format("Missing value for event %s and key %s", eventIdx, keyIdx));
                }
                assertEquals(String.valueOf(keyIdx), value.get());
            }
        }

        kvClient.deleteKeys(root);
    }

    @Test
    @DisplayName("Test late listeners get values")
    public void testLateListenersGetValues() throws Exception {
        KeyValueClient kvClient = consulClient.keyValueClient();
        String root = UUID.randomUUID().toString();

        KVCache nc = KVCache.newCache(
                kvClient, root, 10
        );
        nc.start();

        if (!nc.awaitInitialized(1, TimeUnit.SECONDS)) {
            fail("cache initialization failed");
        }

        final List<Map<String, Value>> events = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            kvClient.putValue(root + "/" + i, String.valueOf(i));
            Synchroniser.pause(Duration.ofMillis(100));
        }

        nc.addListener(events::add);
        assertEquals(1, events.size());

        Map<String, Value> map = events.get(0);
        assertEquals(5, map.size());
        for (int j = 0; j < 5; j++) {
            String keyStr = String.format("%s/%s", root, j);
            String valStr = String.valueOf(j);
            assertEquals(valStr, map.get(keyStr).getValueAsString().get());
        }
        kvClient.deleteKeys(root);
    }

    @Test
    @DisplayName("Test listeners with non-existing keys")
    public void testListenersNonExistingKeys() throws Exception {
        KeyValueClient kvClient = consulClient.keyValueClient();
        String root = UUID.randomUUID().toString();

        KVCache nc = KVCache.newCache(kvClient, root, 10);
        final List<Map<String, Value>> events = new ArrayList<>();
        nc.addListener(events::add);
        nc.start();

        if (!nc.awaitInitialized(1, TimeUnit.SECONDS)) {
            fail("cache initialization failed");
        }

        Synchroniser.pause(Duration.ofMillis(100));

        assertEquals(1, events.size());
        Map<String, Value> map = events.get(0);
        assertEquals(0, map.size());
    }

    @Test
    @DisplayName("Test lifecycle double start throws IllegalStateException")
    public void testLifeCycleDoubleStart() throws Exception {
        KeyValueClient kvClient = consulClient.keyValueClient();
        String root = UUID.randomUUID().toString();

        KVCache nc = KVCache.newCache(kvClient, root, 10);
        assertEquals(ConsulCache.State.latent, nc.getState());
        nc.start();
        assertThat(nc.getState(), anyOf(is(ConsulCache.State.starting), is(ConsulCache.State.started)));

        if (!nc.awaitInitialized(10, TimeUnit.SECONDS)) {
            fail("cache initialization failed");
        }
        assertEquals(ConsulCache.State.started, nc.getState());

        assertThrows(IllegalStateException.class, () -> {
            nc.start();
        });
    }

    @Test
    @DisplayName("Test lifecycle")
    public void testLifeCycle() throws Exception {
        KeyValueClient kvClient = consulClient.keyValueClient();
        String root = UUID.randomUUID().toString();
        final List<Map<String, Value>> events = new ArrayList<>();

        KVCache nc = KVCache.newCache(kvClient, root, 10);
        nc.addListener(events::add);
        assertEquals(ConsulCache.State.latent, nc.getState());

        nc.start();
        assertThat(nc.getState(), anyOf(is(ConsulCache.State.starting), is(ConsulCache.State.started)));

        if (!nc.awaitInitialized(1, TimeUnit.SECONDS)) {
            fail("cache initialization failed");
        }
        assertEquals(ConsulCache.State.started, nc.getState());


        for (int i = 0; i < 5; i++) {
            kvClient.putValue(root + "/" + i, String.valueOf(i));
            Synchroniser.pause(Duration.ofMillis(100));
        }
        assertEquals(6, events.size());

        nc.stop();
        assertEquals(ConsulCache.State.stopped, nc.getState());

        // now assert that we get no more update to the listener
        for (int i = 0; i < 5; i++) {
            kvClient.putValue(root + "/" + i + "-again", String.valueOf(i));
            Synchroniser.pause(Duration.ofMillis(100));
        }

        assertEquals(6, events.size());

        kvClient.deleteKeys(root);

    }

    @Test
    @DisplayName("Ensure cache initialization")
    public void ensureCacheInitialization() throws InterruptedException {
        KeyValueClient keyValueClient = consulClient.keyValueClient();
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        keyValueClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        try (KVCache cache = KVCache.newCache(keyValueClient, key, (int)Duration.ofSeconds(1).getSeconds())) {
            cache.addListener(values -> {
                success.set(isValueEqualsTo(values, value));
                completed.countDown();
            });

            cache.start();
            completed.await(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            keyValueClient.deleteKey(key);
        }

        assertTrue(success.get());
    }

    @ParameterizedTest(name = "queries of {0} seconds")
    @MethodSource("getBlockingQueriesDuration")
    @DisplayName("Check update notifications")
    public void checkUpdateNotifications(int queryDurationSec) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setDaemon(true).setNameFormat("kvcache-itest-%d").build()
        );

        KeyValueClient keyValueClient = consulClient.keyValueClient();
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        String newValue = UUID.randomUUID().toString();
        keyValueClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(2);
        final AtomicBoolean success = new AtomicBoolean(false);

        try (KVCache cache = KVCache.newCache(keyValueClient, key, queryDurationSec)) {
            cache.addListener(values -> {
                success.set(isValueEqualsTo(values, newValue));
                completed.countDown();
            });

            cache.start();
            executor.schedule(() -> keyValueClient.putValue(key, newValue), 3, TimeUnit.SECONDS);
            completed.await(4, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        } finally {
            keyValueClient.deleteKey(key);
            executor.shutdownNow();
        }

        assertTrue(success.get());
    }

    static Stream<Arguments> getBlockingQueriesDuration() {
        return Stream.of(
                Arguments.of(1),
                Arguments.of(10)
        );
    }

    private boolean isValueEqualsTo(Map<String, Value> values, String expectedValue) {
        Value value = values.get("");
        if (value == null) {
            return false;
        }
        Optional<String> valueAsString = value.getValueAsString();
        return valueAsString.isPresent() && expectedValue.equals(valueAsString.get());
    }
}
