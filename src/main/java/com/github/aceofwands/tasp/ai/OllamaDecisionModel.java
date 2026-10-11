package com.github.aceofwands.tasp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Decision model backed by the Ollama {@code /v1/systemone} endpoint (e.g. clef-flash). Only choice questions are supported. */
public class OllamaDecisionModel implements DecisionModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String endpoint;
    private final String modelName;

    public OllamaDecisionModel(HttpClient httpClient, String baseUrl, String modelName) {
        this.httpClient = httpClient;
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/v1/systemone";
        this.modelName = modelName;
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        String model = request.modelName() != null ? request.modelName() : modelName;
        SuccessfulHttpResponse response = httpClient.execute(HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .body(toJson(model, request))
                .build());
        return parse(response.body());
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private static String toJson(String model, DecisionRequest request) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.set("state", MAPPER.valueToTree(request.input()));

        ObjectNode questions = body.putObject("questions");
        for (Map.Entry<String, Question> entry : request.questions().entrySet()) {
            if (!(entry.getValue() instanceof ChoiceQuestion choice)) {
                throw new UnsupportedOperationException(
                        "Only choice questions are supported, got " + entry.getValue().getClass().getSimpleName());
            }
            ObjectNode question = questions.putObject(entry.getKey());
            question.put("type", "choice");
            question.put("instructions", choice.text());
            ObjectNode criteria = question.putObject("criteria");
            choice.options().forEach((option, description) -> {
                if (description == null) {
                    criteria.putNull(option);
                } else {
                    criteria.put(option, description);
                }
            });
        }
        return body.toString();
    }

    private static DecisionResponse parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            DecisionResponse.Builder result = DecisionResponse.builder().modelName(root.path("model").asText(null));

            root.path("answers").properties().forEach(entry -> {
                JsonNode answer = entry.getValue();
                if (!"choice".equals(answer.path("type").asText())) {
                    throw new UnsupportedOperationException("Unsupported answer type: " + answer.path("type").asText());
                }
                Map<String, Double> probabilities = new LinkedHashMap<>();
                answer.path("probabilities").properties()
                        .forEach(p -> probabilities.put(p.getKey(), p.getValue().asDouble()));
                result.answer(entry.getKey(), ChoiceAnswer.builder()
                        .value(answer.path("choice").asText())
                        .probabilities(probabilities)
                        .options(probabilities.keySet())
                        .confidence(answer.hasNonNull("confidence") ? answer.get("confidence").asDouble() : null)
                        .build());
            });

            JsonNode usage = root.path("usage");
            result.tokenUsage(new TokenUsage(usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0)));
            return result.build();
        } catch (UnsupportedOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid response from Ollama system one endpoint: " + json, e);
        }
    }
}