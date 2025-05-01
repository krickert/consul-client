package com.orbitz.consul;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.config.CacheConfig;
import com.orbitz.consul.config.ClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
public abstract class BaseIntegrationTest {

    private final List<String> deregisterServices = new CopyOnWriteArrayList<>();

    protected static Consul client;

    public static GenericContainer<?> consulContainer;
    static {
        consulContainer = new ConsulContainer("hashicorp/consul:latest")
            .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
            .withExposedPorts(8500);
        consulContainer.start();
    }
    public static GenericContainer<?> consulContainerAcl;
    static {
        consulContainerAcl = new ConsulContainer("hashicorp/consul:latest")
            .withCommand("agent", "-dev", "-client", "0.0.0.0", "--enable-script-checks=true")
            .withExposedPorts(8500)
            .withEnv("CONSUL_LOCAL_CONFIG",
                    "{\n" +
                    "  \"acl\": {\n" +
                    "    \"enabled\": true,\n" +
                    "    \"default_policy\": \"deny\",\n" +
                    "    \"tokens\": {\n" +
                    "      \"master\": \"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
            );
        consulContainerAcl.start();
    }

    protected static HostAndPort defaultClientHostAndPort;

    @BeforeAll
    public static void beforeClass() {
        defaultClientHostAndPort = HostAndPort.fromParts("localhost", consulContainer.getFirstMappedPort());
        client = Consul.builder()
                .withHostAndPort(defaultClientHostAndPort)
                .withClientConfiguration(new ClientConfig(CacheConfig.builder().withWatchDuration(Duration.ofSeconds(1)).build()))
                .withReadTimeoutMillis(Duration.ofSeconds(5).toMillis())
                .withWriteTimeoutMillis(Duration.ofSeconds(5).toMillis())
                .build();
    }

    @AfterEach
    public void after() {
        deregisterServices.forEach(client.agentClient()::deregister);
        deregisterServices.clear();
    }

    protected String createAutoDeregisterServiceId() {
        String serviceId = UUID.randomUUID().toString();
        deregisterServices.add(serviceId);

        return serviceId;
    }
}
