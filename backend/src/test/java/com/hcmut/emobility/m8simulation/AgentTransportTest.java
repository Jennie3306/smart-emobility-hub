package com.hcmut.emobility.m8simulation;

import com.fasterxml.jackson.databind.*;
import com.hcmut.emobility.m8simulation.service.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentTransportTest {
    @Test void sendsFunctionSchemaAndReturnsKpiAsToolMessageOverHttp() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();var requests=new ArrayList<JsonNode>();
        var fixture=new AgentPlannerTest();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat",exchange->{
            requests.add(mapper.readTree(exchange.getRequestBody()));
            JsonNode answer;
            try {answer=requests.size()==1?fixture.call("simulation_1",fixture.dispatch(1)):mapper.readTree("{\"role\":\"assistant\",\"content\":\"No further feasible improvement.\"}");}
            catch(Exception e){throw new RuntimeException(e);}
            byte[] data=mapper.writeValueAsBytes(Map.of("choices",List.of(Map.of("message",answer))));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });server.start();
        try {
            var assistant=new ScenarioAssistant(mapper,Validation.buildDefaultValidatorFactory().getValidator(),"http://127.0.0.1:"+server.getAddress().getPort()+"/chat","test-key","gpt-5.6-sol");
            var state=new AgentPlanner(mapper,new SimulationPlanTool(new SimulationEngine()),assistant).extend(fixture.snapshot,fixture.scenario,null);
            assertEquals(2,requests.size());assertEquals("simulate_plan",requests.getFirst().path("tools").get(0).path("function").path("name").asText());
            assertEquals("none",requests.getFirst().path("reasoning_effort").asText());assertFalse(requests.getFirst().has("temperature"));assertFalse(requests.getFirst().path("parallel_tool_calls").asBoolean());assertNotNull(state.best());
            var messages=requests.get(1).path("messages");JsonNode feedback=null;
            for(var message:messages) if("tool".equals(message.path("role").asText())) feedback=message;
            assertNotNull(feedback);assertEquals("simulation_1",feedback.path("tool_call_id").asText());
            assertEquals(10,mapper.readTree(feedback.path("content").asText()).path("kpi").path("requests").asInt());
        } finally {server.stop(0);}
    }
}
