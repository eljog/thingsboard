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
package org.thingsboard.server.transport.snmp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttHandler;
import org.thingsboard.server.common.transport.TransportContext;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.transport.snmp.domain.SnmpGetRequest;
import org.thingsboard.server.transport.snmp.service.SnmpService;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Slf4j
public class SnmpGetManager {

    public static final String RPC_JSON_METHOD_SNMP_GET = "snmpGet";
    public static final String RPC_JSON_METHOD_SNMP_UPDATE_ATTRIBUTE = "snmpUpdateDeviceAttribute";
    public static final String RPC_JSON_METHOD_FIELD = "method";
    public static final String RPC_JSON_PARAM_FIELD = "params";
    @Autowired
    private SnmpService snmpService;

    @Autowired
    private SnmpTransportContext transportContext;

    //    @Value("${transport.snmp.mqtt.broker}")
    private String mqttBroker = "tcp://localhost:1883";

    public void askToListenForRpc(String credentialsId) throws ExecutionException, InterruptedException {
        log.info("Asked to Listen.............: " + credentialsId);
        this.serverSideRpc(credentialsId);
    }

    public JsonObject processRpcRequest(String rpcMessageString, String deviceToken) {
        log.info("SnmpGetManager::processRpcRequest - " + rpcMessageString);

        JsonObject clientResponse = new JsonObject();

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rpcMessageJson = mapper.readTree(rpcMessageString);
            if (rpcMessageJson.has(RPC_JSON_METHOD_FIELD) && rpcMessageJson.has(RPC_JSON_PARAM_FIELD)) {
                SnmpGetRequest snmpGetRequest = mapper.treeToValue(rpcMessageJson.get(RPC_JSON_PARAM_FIELD), SnmpGetRequest.class);
                ;
                switch (rpcMessageJson.get(RPC_JSON_METHOD_FIELD).asText()) {
                    case RPC_JSON_METHOD_SNMP_GET:
                        clientResponse.addProperty("response", snmpService.fetchStringValue(snmpGetRequest));
                        return clientResponse;
                    case RPC_JSON_METHOD_SNMP_UPDATE_ATTRIBUTE:
                        clientResponse.addProperty(snmpGetRequest.getOid(), snmpService.fetchStringValue(snmpGetRequest));
                        // Update Device Attribute
                        transportContext.getTransportService().process(TransportProtos.ValidateDeviceTokenRequestMsg.newBuilder().setToken(deviceToken).build(),
                                new DeviceAuthCallback(transportContext, sessionInfo -> {
                                    TransportService transportService = transportContext.getTransportService();
                                    transportService.process(sessionInfo, JsonConverter.convertToAttributesProto(clientResponse),
                                            new SnmpTransportCallBack());
                                }));
                        return clientResponse;
                    default:
                        clientResponse.addProperty("error", "Unsupported method. Use " + RPC_JSON_METHOD_SNMP_GET + " or " + RPC_JSON_METHOD_SNMP_UPDATE_ATTRIBUTE);
                        return clientResponse;
                }
            } else {
                log.warn("Unable to process RPC payload: " + rpcMessageString);
                clientResponse.addProperty("error", "Unable to process RPC payload");
                return clientResponse;
            }

        } catch (IOException e) {
            log.warn("Invalid RPC payload: " + rpcMessageString, e);
            clientResponse.addProperty("error", "Invalid RPC payload");
            return clientResponse;
        } catch (Exception e) {
            log.warn("Invalid RPC payload: " + rpcMessageString, e);
            clientResponse.addProperty("error", "Invalid RPC payload");
            return clientResponse;
        }
    }

    public void serverSideRpc(String deviceToken) throws ExecutionException, InterruptedException {

        MqttMessageListener listener = new MqttMessageListener();

        MqttClient mqttClient = getMqttClient(deviceToken, listener);
        mqttClient.on("v1/devices/me/rpc/request/+", listener, MqttQoS.AT_LEAST_ONCE).get();

        // Wait until subscription is processed
        // TimeUnit.SECONDS.sleep(3);

        // Wait for RPC call from the server and send the response
        MqttEvent requestFromServer = listener.getEvents().poll(10, TimeUnit.SECONDS);

        String rpcMessage = Objects.requireNonNull(requestFromServer).getMessage();
        log.info("Received Message:: [{}] ", rpcMessage);
        JsonObject snmpGetResponse = processRpcRequest(rpcMessage, deviceToken);


        Integer requestId = Integer.valueOf(Objects.requireNonNull(requestFromServer).getTopic().substring("v1/devices/me/rpc/request/".length()));
        // Send a response to the server's RPC request
        mqttClient.publish("v1/devices/me/rpc/response/" + requestId, Unpooled.wrappedBuffer(snmpGetResponse.toString().getBytes())).get();
        mqttClient.disconnect();
    }

    private MqttClient getMqttClient(String deviceToken, MqttMessageListener listener) throws InterruptedException, ExecutionException {
        MqttClientConfig clientConfig = new MqttClientConfig();
        clientConfig.setClientId("MQTT client from test");
        clientConfig.setUsername(deviceToken);
        MqttClient mqttClient = MqttClient.create(clientConfig, listener);
        mqttClient.connect("localhost", 1883).get();
        return mqttClient;
    }


    @Data
    private class MqttMessageListener implements MqttHandler {
        private final BlockingQueue<MqttEvent> events;

        private MqttMessageListener() {
            events = new ArrayBlockingQueue<>(100);
        }

        @Override
        public void onMessage(String topic, ByteBuf message) {
            log.info("MQTT message [{}], topic [{}]", message.toString(StandardCharsets.UTF_8), topic);
            events.add(new MqttEvent(topic, message.toString(StandardCharsets.UTF_8)));
        }
    }

    @Data
    private class MqttEvent {
        private final String topic;
        private final String message;
    }

    private static class SnmpTransportCallBack implements TransportServiceCallback<Void> {
        @Override
        public void onSuccess(Void msg) {
            log.info("SNMP Telemetry send success");
        }

        @Override
        public void onError(Throwable e) {
            log.error("SNMP Telemetry send failed " + e.getMessage());
        }
    }

    private static class DeviceAuthCallback implements TransportServiceCallback<TransportProtos.ValidateDeviceCredentialsResponseMsg> {
        private final TransportContext transportContext;
        private final Consumer<TransportProtos.SessionInfoProto> onSuccess;

        DeviceAuthCallback(TransportContext transportContext, Consumer<TransportProtos.SessionInfoProto> onSuccess) {
            this.transportContext = transportContext;
            this.onSuccess = onSuccess;
        }

        @Override
        public void onSuccess(TransportProtos.ValidateDeviceCredentialsResponseMsg msg) {
            if (msg.hasDeviceInfo()) {
                UUID sessionId = UUID.randomUUID();
                TransportProtos.DeviceInfoProto deviceInfoProto = msg.getDeviceInfo();
                TransportProtos.SessionInfoProto sessionInfo = TransportProtos.SessionInfoProto.newBuilder()
                        .setNodeId(transportContext.getNodeId())
                        .setTenantIdMSB(deviceInfoProto.getTenantIdMSB())
                        .setTenantIdLSB(deviceInfoProto.getTenantIdLSB())
                        .setDeviceIdMSB(deviceInfoProto.getDeviceIdMSB())
                        .setDeviceIdLSB(deviceInfoProto.getDeviceIdLSB())
                        .setSessionIdMSB(sessionId.getMostSignificantBits())
                        .setSessionIdLSB(sessionId.getLeastSignificantBits())
                        .build();
                onSuccess.accept(sessionInfo);
            } else {
                log.warn("SNMP Unauthorized request");
            }
        }

        @Override
        public void onError(Throwable e) {
            log.warn("SNMP Failed to process request", e);
        }
    }
}
