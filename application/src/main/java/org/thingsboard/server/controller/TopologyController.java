/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thingsboard.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
public class TopologyController {

    private RestTemplate restTemplate = new RestTemplate();

    @Value("${topology.url}")
    private String topologyServiceUrl;

    private final String STORE_TOPOLOGY_ENDPOINT = "/api/topology/";
    private final String GET_TOPOLOGY_ENDPOINT = "/api/topology/";
    private final String GET_SIMPLE_TOPOLOGY_ENDPOINT = "/api/simple-topology/";

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/topology/{macAddress}")
    @ResponseBody
    public ResponseEntity<JsonNode> getToplogy(@PathVariable("macAddress") String macAddress, @RequestParam(name = "full", required = false) boolean full) {
        return restTemplate.getForEntity(topologyServiceUrl + GET_TOPOLOGY_ENDPOINT + macAddress + "?full=" + full, JsonNode.class);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/simple-topology/{macAddress}")
    @ResponseBody
    public ResponseEntity<JsonNode> fetchSimplifiedNodesAndEdges(@PathVariable("macAddress") String macAddress, @RequestParam(name = "full", required = false) boolean full) {
        return restTemplate.getForEntity(topologyServiceUrl + GET_SIMPLE_TOPOLOGY_ENDPOINT + macAddress + "?full=" + full, JsonNode.class);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping("/topology")
    @ResponseBody
    public ResponseEntity<JsonNode> storeTopology(@RequestBody JsonNode topologyJson) {
        HttpEntity<JsonNode> postEntity = new HttpEntity<>(topologyJson);
        return restTemplate.exchange(topologyServiceUrl + STORE_TOPOLOGY_ENDPOINT, HttpMethod.POST, postEntity, JsonNode.class);
    }

}
