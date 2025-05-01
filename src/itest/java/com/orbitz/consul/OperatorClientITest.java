package com.orbitz.consul;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.orbitz.consul.model.operator.RaftServer;

import java.util.List;

public class OperatorClientITest extends BaseIntegrationTest {

    private OperatorClient operatorClient;

    @BeforeEach
    public void setUp() {
        operatorClient = client.operatorClient();
    }

    @Test
    public void shouldGetRaftConfiguration() {
        var raftConfiguration = operatorClient.getRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    @Test
    public void shouldGetRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        var raftConfiguration = operatorClient.getRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    private String getFirstDatacenter() {
        List<String> datacenters = client.catalogClient().getDatacenters();
        assertFalse(datacenters.isEmpty());
        return datacenters.get(0);
    }

    @Test
    public void shouldGetStaleRaftConfiguration() {
        var raftConfiguration = operatorClient.getStaleRaftConfiguration();

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }

    @Test
    public void shouldGetStaleRaftConfigurationForDatacenter() {
        String datacenter = getFirstDatacenter();

        var raftConfiguration = operatorClient.getStaleRaftConfiguration(datacenter);

        List<RaftServer> servers = raftConfiguration.servers();
        assertFalse(servers.isEmpty());
    }
}