package com.orbitz.consul;

import java.time.Duration;

public class Synchroniser {

    public static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Pauses for a longer duration to allow Consul to process requests in containerized environments.
     * This is particularly useful for tests that register services and checks.
     */
    public static void pauseForService() {
        pause(Duration.ofSeconds(1));
    }
}
