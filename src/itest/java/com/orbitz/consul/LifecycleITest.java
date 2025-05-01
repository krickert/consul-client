package com.orbitz.consul;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import okhttp3.ConnectionPool;
import okhttp3.internal.Util;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LifecycleITest extends BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleITest.class);

    @Test
    @DisplayName("Should be destroyable")
    public void shouldBeDestroyable() {
        Consul client = Consul.builder().withHostAndPort(defaultClientHostAndPort).build();

        // Just verify that destroy() doesn't throw an exception
        client.destroy();

        // No assertion needed since our Consul class doesn't have an isDestroyed() method
    }

    @Test
    @DisplayName("Should destroy the executor service when destroy method is invoked")
    public void shouldDestroyTheExecutorServiceWhenDestroyMethodIsInvoked() {
        ConnectionPool connectionPool = new ConnectionPool();
        ExecutorService executorService = mock(ExecutorService.class);

        Consul client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withExecutorService(executorService)
                .withConnectionPool(connectionPool)
                .build();

        client.destroy();

        // Verify that the executor service was shut down
        verify(executorService).shutdownNow();
        verifyNoMoreInteractions(executorService);
    }

    @Test
    @DisplayName("Should be destroyable with custom executor service")
    public void shouldBeDestroyableWithCustomExecutorService() throws InterruptedException {
        ConnectionPool connectionPool = new ConnectionPool();
        SynchronousQueue<Runnable> workQueue = new SynchronousQueue<>();
        ExecutorService executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                workQueue, Util.threadFactory("OkHttp Dispatcher", false));

        executorService.execute(() -> {
            Thread currentThread = Thread.currentThread();
            LOG.info("This is a Task printing a message in Thread {}", currentThread);
        });

        Consul client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withExecutorService(executorService)
                .withConnectionPool(connectionPool)
                .build();

        client.destroy();

        // Verify that the executor service was terminated
        boolean wasTerminated = executorService.awaitTermination(1, TimeUnit.SECONDS);
        assertTrue(wasTerminated, "Executor service should be terminated");
    }
}
