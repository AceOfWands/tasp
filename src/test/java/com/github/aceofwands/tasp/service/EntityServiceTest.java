package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.model.EntityType;
import com.github.aceofwands.tasp.model.OntologyEntity;
import com.github.aceofwands.tasp.repository.EntityRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityServiceTest {

    @Mock
    private EntityRepository repository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EntityClassifier entityClassifier;

    private EntityService entityService;

    @BeforeEach
    void setUp() {
        entityService = new EntityService(repository, embeddingModel, entityClassifier);
        ReflectionTestUtils.setField(entityService, "topK", 5);
        ReflectionTestUtils.setField(entityService, "maxIterations", 3);
        ReflectionTestUtils.setField(entityService, "confidenceThreshold", 0.6);
        ReflectionTestUtils.setField(entityService, "expandThresholdFactor", 1.5);
        ReflectionTestUtils.setField(entityService, "generatedBaseIri", "https://github.com/aceofwands/tasp#generated/");

        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.25f, 0.75f})));
    }

    @Test
    void resolveClassThrowsWhenEntityMissingAndStoringDisabled() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), anyInt()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> entityService.resolveClass("a missing class"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Could not ground entity");

        verify(repository, never()).save(any(OntologyEntity.class));
        verifyNoInteractions(entityClassifier);
    }

    @Test
    void resolveClassStoresMissingEntityWhenEnabled() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), anyInt()))
                .thenReturn(List.of());
        when(repository.save(any(OntologyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, OntologyEntity.class));

        String resolvedClass = entityService.resolveClass("un cardiologo pediatrico", true);

        ArgumentCaptor<OntologyEntity> captor = ArgumentCaptor.forClass(OntologyEntity.class);
        verify(repository).save(captor.capture());

        OntologyEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getType()).isEqualTo(EntityType.CLASS);
        assertThat(savedEntity.getEmbedding()).containsExactly(0.25f, 0.75f);
        assertThat(savedEntity.getIri())
                .startsWith("https://github.com/aceofwands/tasp#generated/class/un-cardiologo-pediatrico-");
        assertThat(resolvedClass).isEqualTo(savedEntity.getIri());
        verifyNoInteractions(entityClassifier);
    }

    private EntityRepository.OntologyQueryResultItem candidate(String iri) {
        var item = new EntityRepository.OntologyQueryResultItem();
        item.setIri(iri);
        item.setDescription("a medical doctor");
        return item;
    }

    private void candidatesFound() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), anyInt()))
                .thenReturn(List.of(candidate("urn:doctor")));
    }

    @Test
    void confidentClassificationReturnsTheIri() {
        candidatesFound();
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification("urn:doctor", 0.9));

        assertThat(entityService.resolveClass("a doctor")).isEqualTo("urn:doctor");
    }

    private List<EntityRepository.OntologyQueryResultItem> candidates(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> candidate("urn:" + i)).toList();
    }

    @Test
    void noneWidensTheRetrievalUntilAClearMatch() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), eq(5))).thenReturn(candidates(5));
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), eq(10))).thenReturn(candidates(10));
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification(null, 0.9))
                .thenReturn(new EntityClassifier.Classification("urn:7", 0.95));

        assertThat(entityService.resolveClass("a doctor")).isEqualTo("urn:7");
    }

    @Test
    void expandedRoundsRequireAHigherConfidence() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), eq(5))).thenReturn(candidates(5));
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), eq(10))).thenReturn(candidates(10));
        // 0.7 passes the base threshold (0.6) but not the raised one (0.9) of the second round
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification(null, 0.9))
                .thenReturn(new EntityClassifier.Classification("urn:7", 0.7));

        assertThatThrownBy(() -> entityService.resolveClass("a doctor"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void refineIsRaisedOnlyWhenTheRoundIsAmbiguousAndTheCallerCanRefine() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), eq(5))).thenReturn(candidates(5));
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), eq(10))).thenReturn(candidates(10));
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification(null, 0.9))
                .thenReturn(new EntityClassifier.Classification("urn:7", 0.7));

        assertThatThrownBy(() -> entityService.resolveClass("a doctor", false, true))
                .isInstanceOf(EntityResolutionException.class)
                .hasMessageContaining("REFINE");
    }

    @Test
    void expansionStopsWhenEveryEntityWasAlreadyRetrieved() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), anyInt())).thenReturn(candidates(2));
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification(null, 0.9));

        assertThatThrownBy(() -> entityService.resolveClass("a doctor")).isInstanceOf(EntityNotFoundException.class);

        verify(repository, times(1)).findTopK(any(float[].class), eq(EntityType.CLASS), anyInt());
    }

    @Test
    void exhaustedExpansionStoresTheEntityWhenAllowed() {
        when(repository.findTopK(any(float[].class), eq(EntityType.CLASS), anyInt()))
                .thenAnswer(invocation -> candidates(invocation.getArgument(2)));
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification(null, 0.9));
        when(repository.save(any(OntologyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, OntologyEntity.class));

        assertThat(entityService.resolveClass("a doctor", true)).contains("/class/");

        verify(entityClassifier, times(3)).classify(any(String.class), any());
    }

    @Test
    void ambiguousMatchIsHandedBackToACallerThatCanRefine() {
        candidatesFound();
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification("urn:doctor", 0.3));

        assertThatThrownBy(() -> entityService.resolveClass("a doctor", true, true))
                .isInstanceOf(EntityResolutionException.class)
                .hasMessageContaining("REFINE")
                .hasMessageContaining("a medical doctor");
        verify(repository, never()).save(any(OntologyEntity.class));
    }

    @Test
    void ambiguousMatchIsStoredWhenTheCallerCannotRefine() {
        candidatesFound();
        when(entityClassifier.classify(any(String.class), any()))
                .thenReturn(new EntityClassifier.Classification("urn:doctor", 0.3));
        when(repository.save(any(OntologyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, OntologyEntity.class));

        assertThat(entityService.resolveClass("a doctor", true)).contains("/class/");
    }
    @Test
    void classifierFailurePropagates() {
        candidatesFound();
        when(entityClassifier.classify(any(String.class), any())).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> entityService.resolveClass("a doctor", true))
                .isInstanceOf(IllegalStateException.class);
    }
}