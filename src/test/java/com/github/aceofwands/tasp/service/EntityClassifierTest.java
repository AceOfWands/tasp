package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.repository.EntityRepository;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityClassifierTest {

    private final DecisionModel decisionModel = mock(DecisionModel.class);

    private static EntityRepository.OntologyQueryResultItem candidate(String iri, String description) {
        var item = new EntityRepository.OntologyQueryResultItem();
        item.setIri(iri);
        item.setDescription(description);
        return item;
    }

    private void answerWith(String choice, Map<String, Double> probabilities) {
        when(decisionModel.decide(any(DecisionRequest.class))).thenReturn(DecisionResponse.builder()
                .answer("entity", ChoiceAnswer.builder()
                        .value(choice).probabilities(probabilities).options(probabilities.keySet()).build())
                .build());
    }

    private final List<EntityRepository.OntologyQueryResultItem> candidates = List.of(
            candidate("urn:a", "a surgeon"), candidate("urn:b", "a medical doctor"));

    @Test
    void mapsChosenOptionToCandidateIri() {
        answerWith("c1", Map.of("c0", 0.1, "c1", 0.85, "none", 0.05));

        var classification = new EntityClassifier(decisionModel).classify("a doctor", candidates);

        assertThat(classification.iri()).isEqualTo("urn:b");
        assertThat(classification.probability()).isEqualTo(0.85);
        var request = ArgumentCaptor.forClass(DecisionRequest.class);
        verify(decisionModel).decide(request.capture());
        assertThat(request.getValue().input()).isEqualTo("a doctor");
    }

    @Test
    void noneOptionYieldsNullIri() {
        answerWith("none", Map.of("c0", 0.1, "c1", 0.1, "none", 0.8));

        var classification = new EntityClassifier(decisionModel).classify("a doctor", candidates);

        assertThat(classification.iri()).isNull();
        assertThat(classification.probability()).isEqualTo(0.8);
    }

    @Test
    void modelFailureIsReportedNotSwallowed() {
        when(decisionModel.decide(any(DecisionRequest.class))).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> new EntityClassifier(decisionModel).classify("a doctor", candidates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Entity classifier unavailable");
    }
}