package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.repository.EntityRepository;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Picks the entity matching a description among retrieved candidates with a fast decision model. */
@Component
public class EntityClassifier {
    private static final String QUESTION = "entity";
    private static final String NONE = "none";

    /** {@code iri} is null when the model judged that no candidate matches; {@code probability} belongs to the chosen option. */
    public record Classification(String iri, double probability) {
    }

    private final DecisionModel decisionModel;

    public EntityClassifier(@Qualifier("entityDecisionModel") DecisionModel decisionModel) {
        this.decisionModel = decisionModel;
    }

    /** Fails with an {@link IllegalStateException} when the decision model cannot be reached. */
    public Classification classify(String description, List<EntityRepository.OntologyQueryResultItem> candidates) {
        Map<String, String> options = new LinkedHashMap<>();
        Map<String, String> iriByOption = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            String key = "c" + i;
            options.put(key, candidates.get(i).getDescription());
            iriByOption.put(key, candidates.get(i).getIri());
        }
        options.put(NONE, "None of the other options is the entity described in the input");

        try {
            ChoiceAnswer answer = decisionModel.decide(DecisionRequest.builder()
                    .input(description)
                    .question(QUESTION, ChoiceQuestion.of("Which option is the entity described in the input?", options))
                    .build()).choice(QUESTION);
            return new Classification(iriByOption.get(answer.value()), answer.probabilityOf(answer.value()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Entity classifier unavailable: " + e.getMessage(), e);
        }
    }
}