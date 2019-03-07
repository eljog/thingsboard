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
package org.thingsboard.server.transport.snmp.util;

import lombok.extern.slf4j.Slf4j;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.transport.snmp.domain.Protocol;

import java.io.IOException;

@Slf4j
@Component
public class SnmpGetUtil {

//    @Value("${transport.snmp.community}")
    private String community = "public";

    public String getStringValue(OID oid, Address address, Protocol protocol) throws IOException {
        Snmp snmp = this.openSnmp(protocol);
        String responseValue = null;

        // Create Target Address object
        CommunityTarget communityTarget = new CommunityTarget();
        communityTarget.setCommunity(new OctetString(community));
        communityTarget.setVersion(SnmpConstants.version2c);
        communityTarget.setAddress(address);
        communityTarget.setRetries(2);
        communityTarget.setTimeout(1000);

        // Create the PDU object
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(oid));
        pdu.setType(PDU.GET);
        pdu.setRequestID(new Integer32(1));


        log.info("Sending Request to Agent...");
        ResponseEvent response = snmp.get(pdu, communityTarget);

        // Process Agent Response
        if (response != null) {
            PDU responsePDU = response.getResponse();

            if (responsePDU != null) {
                int errorStatus = responsePDU.getErrorStatus();
                int errorIndex = responsePDU.getErrorIndex();
                String errorStatusText = responsePDU.getErrorStatusText();

                if (errorStatus == PDU.noError) {
                    responseValue = responsePDU.getVariableBindings().firstElement().toValueString();
                    log.info("Snmp Get Response = " + responseValue);
                } else {
                    log.warn("Error: Request Failed with Error Status = " + errorStatus + ", Error Index = " + errorIndex + " and Error Status Text = " + errorStatusText);
                }
            } else {
                log.warn("Error: Response PDU is null");
            }
        } else {
            log.warn("Error: Agent Timeout ");
        }
        snmp.close();

        return responseValue;
    }

    private Snmp openSnmp(Protocol protocol) throws IOException {
        TransportMapping udpTransportMapping;
        TransportMapping tcpTransportMapping;

        switch (protocol) {
            case TCP:
                tcpTransportMapping = new DefaultTcpTransportMapping();
                if (!tcpTransportMapping.isListening())
                    tcpTransportMapping.listen();

                return new Snmp(tcpTransportMapping);

            case UDP:
                udpTransportMapping = new DefaultUdpTransportMapping();
                udpTransportMapping.listen();
                return new Snmp(udpTransportMapping);

            default:
                udpTransportMapping = new DefaultUdpTransportMapping();
                udpTransportMapping.listen();
                return new Snmp(udpTransportMapping);
        }
    }
}
