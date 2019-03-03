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
package org.thingsboard.topology.repository;


import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.thingsboard.topology.domain.TopologyDevice;

@Repository
public interface TopologyDeviceRepository extends CrudRepository<TopologyDevice, Long> {
    TopologyDevice findByMacAddress(String macAddress);

    @Query("match(current:TopologyDevice {macAddress: {0}})<-[:CONNECTS*]-(root:TopologyDevice) " +
            "where not((root)<-[:CONNECTS]-(:TopologyDevice)) return root limit 1")
    TopologyDevice findRootByMacAddress(String macAddress);
}
