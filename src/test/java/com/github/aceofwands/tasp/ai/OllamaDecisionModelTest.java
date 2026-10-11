package com.github.aceofwands.tasp.ai;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaDecisionModelTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = """
            {"model":"clef-flash","answers":{"entity":{"type":"choice","choice":"c1",
            "probabilities":{"c0":0.1,"c1":0.85,"none":0.05},"confidence":0.7}},
            "usage":{"input_tokens":12,"output_tokens":0}}""";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private OllamaDecisionModel model() {
        return new OllamaDecisionModel(
                HttpClientBuilderLoader.loadHttpClientBuilder().build(),
                "http://localhost:" + server.getAddress().getPort() + "/",
                "clef-flash");
    }

    private DecisionRequest request() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("c0", "a surgeon");
        options.put("c1", "a medical doctor");
        options.put("none", "nothing fits");
        return DecisionRequest.builder()
                .input("a doctor")
                .question("entity", ChoiceQuestion.of("Which option?", options))
                .build();
    }

    @Test
    void callsSystemOneAndMapsChoiceAnswer() {
        ChoiceAnswer answer = model().decide(request()).choice("entity");

        assertThat(path.get()).isEqualTo("/v1/systemone");
        assertThat(body.get())
                .contains("\"model\":\"clef-flash\"")
                .contains("\"state\":\"a doctor\"")
                .contains("\"type\":\"choice\"")
                .contains("\"instructions\":\"Which option?\"")
                .contains("\"c1\":\"a medical doctor\"");
        assertThat(answer.value()).isEqualTo("c1");
        assertThat(answer.probabilityOf("c1")).isEqualTo(0.85);
        assertThat(answer.confidence()).isEqualTo(0.7);
    }

    @Test
    void httpErrorsPropagate() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertThatThrownBy(() -> model().decide(request())).isInstanceOf(RuntimeException.class);
    }
}