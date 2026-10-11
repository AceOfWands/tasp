package com.github.aceofwands.tasp.repository;

import com.github.aceofwands.tasp.model.EntityType;
import com.github.aceofwands.tasp.model.OntologyEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EntityRepository extends CrudRepository<@NonNull OntologyEntity, @NonNull String> {
    @EqualsAndHashCode(callSuper = true)
    @Data
    class OntologyQueryResultItem extends OntologyEntity {
        private double score;
    }

    @Query(value = """
        SELECT *, (embedding <=> :queryVector) AS score
        FROM ontology_entities
        WHERE entity_type = :type
        ORDER BY embedding <=> :queryVector
        LIMIT :k
    """, nativeQuery = true)
    List<OntologyQueryResultItem> findTopK(
            @Param("queryVector") float[] queryVector,
            @Param("type") EntityType type,
            @Param("k") int k
    );
}
