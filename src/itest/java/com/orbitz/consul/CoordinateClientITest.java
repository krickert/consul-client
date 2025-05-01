package com.orbitz.consul;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import com.orbitz.consul.model.coordinate.Coordinate;
import com.orbitz.consul.model.coordinate.Datacenter;

import java.util.List;

public class CoordinateClientITest extends BaseIntegrationTest {

    @Test
    public void shouldGetDatacenters() {
        List<Datacenter> datacenters = client.coordinateClient().getDatacenters();
        assertFalse(datacenters.isEmpty());
    }

    @Test
    public void shouldGetNodes() {
        List<Coordinate> nodes = client.coordinateClient().getNodes();
        assertNotNull(nodes);
    }

    @Test
    public void shouldGetNodesForDatacenter() {
        String datacenter = client.coordinateClient().getDatacenters().get(0).getDatacenter();

        List<Coordinate> nodes = client.coordinateClient().getNodes(datacenter);
        assertNotNull(nodes);
    }
}