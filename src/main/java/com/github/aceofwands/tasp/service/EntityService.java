package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.model.EntityType;
import com.github.aceofwands.tasp.model.OntologyEntity;
import com.github.aceofwands.tasp.repository.EntityRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Getter
public class EntityService {
    private static final double MAX_THRESHOLD = 0.99;

    private final EntityRepository repository;
    private final EmbeddingModel embeddingModel;
    private final EntityClassifier entityClassifier;

    @Value("${tasp.entity-resolution.top-k:5}")
    private int topK;

    @Value("${tasp.entity-resolution.confidence-threshold:0.6}")
    private double confidenceThreshold;

    /** Maximum number of EXPAND rounds per description. */
    @Value("${tasp.entity-resolution.max-iterations:3}")
    private int maxIterations;

    /** Factor applied to the confidence threshold at every EXPAND round: more candidates call for more certainty. */
    @Value("${tasp.entity-resolution.expand-threshold-factor:1.1}")
    private double expandThresholdFactor;

    @Value("${tasp.entity-resolution.generated-base-iri:https://github.com/aceofwands/tasp#generated/}")
    private String generatedBaseIri;

    /**
     * When the classifier finds no fitting candidate the search is widened (EXPAND): more candidates are retrieved
     * and the confidence required to accept one grows, for at most {@code maxIterations} rounds.
     * When a candidate stands out but not confidently enough the description is ambiguous (REFINE):
     * a caller that can reformulate it receives an {@link EntityResolutionException}.
     * Otherwise the entity is stored if allowed, or reported as not found.
     */
    private String resolveIri(String description, EntityType type, boolean storeIfMissing, boolean callerCanRefine) {
        float[] vector = embeddingModel.embed(description).content().vector();
        int limit = topK;
        double threshold = confidenceThreshold;

        for (int round = 0; round < maxIterations; round++) {
            List<EntityRepository.OntologyQueryResultItem> candidates = repository.findTopK(vector, type, limit);
            if (candidates.isEmpty()) {
                break;
            }

            var classification = entityClassifier.classify(description, candidates);
            if (classification.iri() != null) {
                if (classification.probability() >= threshold) {
                    return classification.iri();
                }
                if (callerCanRefine) {
                    throw new EntityResolutionException(description,
                            candidates.stream().map(EntityRepository.OntologyQueryResultItem::getDescription).toList());
                }
                break;
            }

            if (candidates.size() < limit) {
                break;
            }
            limit *= 2;
            threshold = Math.min(MAX_THRESHOLD, threshold * expandThresholdFactor);
        }

        if (storeIfMissing) {
            return storeMissingEntity(description, type);
        }
        throw new EntityNotFoundException("Could not ground entity: " + description);
    }
    /** Returns the IRI of the class that best matches the description. */
    public String resolveClass(String description) {
        return resolveClass(description, false);
    }

    public String resolveClass(String description, boolean storeIfMissing) {
        return resolveIri(description, EntityType.CLASS, storeIfMissing, false);
    }

    /** For callers that can reformulate an ambiguous description: it is handed back as an {@link EntityResolutionException}. */
    public String resolveClass(String description, boolean storeIfMissing, boolean callerCanRefine) {
        return resolveIri(description, EntityType.CLASS, storeIfMissing, callerCanRefine);
    }

    public String resolveProperty(String description) {
        return resolveProperty(description, false);
    }

    public String resolveProperty(String description, boolean storeIfMissing) {
        return resolveIri(description, EntityType.PROPERTY, storeIfMissing, false);
    }

    /** For callers that can reformulate an ambiguous description: it is handed back as an {@link EntityResolutionException}. */
    public String resolveProperty(String description, boolean storeIfMissing, boolean callerCanRefine) {
        return resolveIri(description, EntityType.PROPERTY, storeIfMissing, callerCanRefine);
    }

    /** For callers that can reformulate an ambiguous description: it is handed back as an {@link EntityResolutionException}. */
    public String resolveDataProperty(String description, boolean storeIfMissing, boolean callerCanRefine) {
        return resolveIri(description, EntityType.DATA_PROPERTY, storeIfMissing, callerCanRefine);
    }

    public String resolveIndividual(String description) {
        return resolveIndividual(description, false);
    }

    public String resolveIndividual(String description, boolean storeIfMissing) {
        return resolveIri(description, EntityType.INDIVIDUAL, storeIfMissing, false);
    }

    /** For callers that can reformulate an ambiguous description: it is handed back as an {@link EntityResolutionException}. */
    public String resolveIndividual(String description, boolean storeIfMissing, boolean callerCanRefine) {
        return resolveIri(description, EntityType.INDIVIDUAL, storeIfMissing, callerCanRefine);
    }

    private String storeMissingEntity(String description, EntityType type) {
        OntologyEntity entity = new OntologyEntity();
        entity.setIri(buildGeneratedIri(description, type));
        entity.setType(type);
        entity.setDescription(description);
        entity.setEmbedding(embeddingModel.embed(description).content().vector());

        return repository.save(entity).getIri();
    }

    private String buildGeneratedIri(String description, EntityType type) {
        String base = generatedBaseIri.endsWith("#") || generatedBaseIri.endsWith("/")
                ? generatedBaseIri
                : generatedBaseIri + "#";
        String normalizedDescription = description == null ? "" : description.strip();
        String slug = slugify(normalizedDescription);
        UUID stableId = UUID.nameUUIDFromBytes(
                (type.name() + ":" + normalizedDescription).getBytes(StandardCharsets.UTF_8)
        );
        return base + type.name().toLowerCase(Locale.ROOT) + "/" + slug + "-" + stableId;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (normalized.isBlank()) {
            return "entity";
        }

        return normalized.substring(0, Math.min(normalized.length(), 64));
    }
}
