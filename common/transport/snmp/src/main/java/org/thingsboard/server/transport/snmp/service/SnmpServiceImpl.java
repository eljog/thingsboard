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
package org.thingsboard.server.transport.snmp.service;

import lombok.extern.slf4j.Slf4j;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.UdpAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.transport.snmp.domain.Protocol;
import org.thingsboard.server.transport.snmp.domain.SnmpGetRequest;
import org.thingsboard.server.transport.snmp.util.SnmpGetUtil;

import java.io.IOException;

@Slf4j
@Service
public class SnmpServiceImpl implements SnmpService {

    @Autowired
    private SnmpGetUtil snmpGetUtil;

    @Override
    public String fetchStringValue(SnmpGetRequest snmpGetRequest) throws IOException {
        Address address = new UdpAddress(snmpGetRequest.getIp() + "/" + snmpGetRequest.getPort());
        OID oid = new OID(snmpGetRequest.getOid());

        return snmpGetUtil.getStringValue(oid, address, Protocol.UDP);
    }
}
