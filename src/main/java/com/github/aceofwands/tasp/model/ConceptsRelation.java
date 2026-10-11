package com.github.aceofwands.tasp.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class ConceptsRelation {
    public enum ConceptRelationType {
        EQUIVALENT_TO, SUBCLASS_OF, EQUALS_TO
    }

    @Description("The subject concept in the relation.")
    private Statement subjectConcept;
    @Description("The object concept in the relation.")
    private Statement objectConcept;
    @Description("The type of relation between the source and object concepts. Possible values: EQUIVALENT_TO, SUBCLASS_OF, EQUALS_TO.")
    private ConceptRelationType relationType;
}
