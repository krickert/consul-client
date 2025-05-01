package com.orbitz.consul;

import com.google.common.net.HostAndPort;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.health.HealthCheck;
import com.orbitz.consul.model.health.ServiceHealth;
import com.orbitz.consul.option.ImmutableQueryOptions;
import com.orbitz.consul.option.QueryOptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.orbitz.consul.Consul.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthITest extends BaseIntegrationTest {

    private static final List<String> NO_TAGS = Collections.emptyList();
    private static final Map<String, String> NO_META = Collections.emptyMap();

    @Test
    @DisplayName("Should fetch passing node")
    public void shouldFetchPassingNode() throws NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = createAutoDeregisterServiceId();

        client.agentClient().register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        Synchroniser.pauseForService();
        client.agentClient().pass(serviceId);

        Consul client2 = builder().withHostAndPort(HostAndPort.fromParts("localhost", consulContainer.getFirstMappedPort())).build();
        String serviceId2 = UUID.randomUUID().toString(); // Not using createAutoDeregisterServiceId() for client2

        try {
            client2.agentClient().register(8080, 20L, serviceName, serviceId2, NO_TAGS, NO_META);
            Synchroniser.pauseForService();
            client2.agentClient().fail(serviceId2);

            ConsulResponse<List<ServiceHealth>> response = client2.healthClient().getHealthyServiceInstances(serviceName);
            assertHealthExistsWithServiceId(serviceId, response);
        } finally {
            // Clean up the service registered with client2
            client2.agentClient().deregister(serviceId2);
        }
    }

    @Test
    @DisplayName("Should fetch node")
    public void shouldFetchNode() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        Synchroniser.pauseForService();
        client.agentClient().pass(serviceId);

        ConsulResponse<List<ServiceHealth>> response = client.healthClient().getAllServiceInstances(serviceName);
        assertHealth(serviceId, response);

        client.agentClient().deregister(serviceId);
    }

    @Test
    @DisplayName("Should fetch node by datacenter")
    public void shouldFetchNodeDatacenter() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        Synchroniser.pauseForService();
        client.agentClient().pass(serviceId);

        ConsulResponse<List<ServiceHealth>> response = client.healthClient().getAllServiceInstances(serviceName,
                ImmutableQueryOptions.builder().datacenter("dc1").build());
        assertHealth(serviceId, response);
        client.agentClient().deregister(serviceId);
    }

    @Test
    @DisplayName("Should fetch node with blocking query")
    public void shouldFetchNodeBlock() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        Synchroniser.pauseForService();
        client.agentClient().pass(serviceId);

        ConsulResponse<List<ServiceHealth>> response = client.healthClient().getAllServiceInstances(serviceName,
                QueryOptions.blockSeconds(2, new BigInteger("0")).datacenter("dc1").build());
        assertHealth(serviceId, response);
        client.agentClient().deregister(serviceId);
    }

    @Test
    @DisplayName("Should fetch checks for service with blocking query")
    public void shouldFetchChecksForServiceBlock() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        Registration.RegCheck check = Registration.RegCheck.ttl(5);
        Registration registration = ImmutableRegistration
                .builder()
                .check(check)
                .port(8080)
                .name(serviceName)
                .id(serviceId)
                .build();

        client.agentClient().register(registration);
        Synchroniser.pauseForService();
        client.agentClient().pass(serviceId);

        boolean found = false;
        ConsulResponse<List<HealthCheck>> response = client.healthClient().getServiceChecks(serviceName,
                QueryOptions.blockSeconds(20, new BigInteger("0")).datacenter("dc1").build());

        List<HealthCheck> checks = response.getResponse();
        assertEquals(1, checks.size());
        for(HealthCheck ch : checks) {
            if(ch.getServiceId().isPresent() && ch.getServiceId().get().equals(serviceId)) {
                found = true;
            }
        }
        assertTrue(found);
        client.agentClient().deregister(serviceId);
    }

    @Test
    @DisplayName("Should fetch checks by state")
    public void shouldFetchByState() throws UnknownHostException, NotRegisteredException {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId, NO_TAGS, NO_META);
        Synchroniser.pauseForService();
        client.agentClient().warn(serviceId);

        boolean found = false;
        ConsulResponse<List<HealthCheck>> response = client.healthClient().getChecksByState(State.WARN);

        for(HealthCheck healthCheck : response.getResponse()) {
            if(healthCheck.getServiceId().isPresent() && healthCheck.getServiceId().get().equals(serviceId)) {
                found = true;
            }
        }

        assertTrue(found);
        client.agentClient().deregister(serviceId);
    }

    private void assertHealth(String serviceId, ConsulResponse<List<ServiceHealth>> response) {
        boolean found = false;
        List<ServiceHealth> nodes = response.getResponse();

        assertEquals(1, nodes.size());

        for(ServiceHealth health : nodes) {
            if(health.getService().getId().equals(serviceId)) {
                found = true;
            }
        }

        assertTrue(found);
    }

    private void assertHealthExistsWithServiceId(String serviceId, ConsulResponse<List<ServiceHealth>> response) {
        List<ServiceHealth> serviceHealthList = response.getResponse();

        assertEquals(1, serviceHealthList.size(), "Expected exactly one service health in the response");

        boolean found = false;
        for (ServiceHealth serviceHealth : serviceHealthList) {
            if (serviceHealth.getService().getId().equals(serviceId)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "Expected to find ServiceHealth with serviceId " + serviceId);
    }
}
