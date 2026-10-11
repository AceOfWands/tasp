package com.github.aceofwands.tasp.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

@Data
public class OntologicalDecomposition {
    @Description("Atomic facts about specific individuals: one entry per fact. Example: 'Mario is a surgeon', 'Dr. Rossi works at Gemelli Hospital'")
    private List<AssertionFact> assertions;

    @Description("General definitions, hierarchies, or constraints between classes. Example: 'Surgeons are doctors'")
    private List<ConceptsRelation> terminologies;

    @Description("Questions or verification requests expressed by the user. Example: 'Who are the doctors?'")
    private List<Statement> queries;
}
