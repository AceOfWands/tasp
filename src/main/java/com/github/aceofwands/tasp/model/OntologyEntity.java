package com.github.aceofwands.tasp.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ontology_entities")
@Data
public class OntologyEntity {
    @Id
    private String iri;

    @Column(name = "entity_type")
    @Enumerated(EnumType.STRING)
    private EntityType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 384)
    private float[] embedding;
}
