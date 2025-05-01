package com.orbitz.consul;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ConsulITest extends BaseIntegrationTest {

    @Test
    public void shouldDestroy() {
        client.destroy();

        assertTrue(client.isDestroyed());
    }
}