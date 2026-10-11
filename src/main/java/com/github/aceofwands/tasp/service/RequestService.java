package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.ai.OntologicalDecompositionAiService;
import com.github.aceofwands.tasp.model.AssertionFact;
import com.github.aceofwands.tasp.model.ConceptsRelation;
import com.github.aceofwands.tasp.model.OntologicalDecomposition;
import com.github.aceofwands.tasp.model.Request;
import lombok.RequiredArgsConstructor;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.system.Txn;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RequestService {
    private static final String ONTOLOGY_NAMESPACE = "https://github.com/aceofwands/tasp#";

    private final OntologicalDecompositionAiService ontologicalDecompositionAiService;
    private final AiService aiService;
    private final EntityService entityService;
    private final Dataset knowledgeBase;
    private final OntologyValidator ontologyValidator;

    public OntologicalDecomposition handleRequest(Request request) {
        return ontologicalDecompositionAiService.parse(request.getMessage());
    }

    public OntologicalDecomposition handleCqByQc(
            String domainDescription,
            List<String> competencyQuestions
    ) {
        OntModel staging = OntModelFactory.createModel();
        OntModel queries = OntModelFactory.createModel();
        AtomicInteger queryCounter = new AtomicInteger();

        OntologicalDecomposition decomposition = null;
        if (competencyQuestions != null) {
            for (String competencyQuestion : competencyQuestions) {
                decomposition = ontologicalDecompositionAiService.importDomain(
                        domainDescription,
                        competencyQuestion
                );
                collectAxioms(staging, queries, decomposition, queryCounter);
                // Fallisce alla prima CQ inconsistente: nulla viene committato e le CQ successive non vengono elaborate
                ontologyValidator.validate(knowledgeBase, staging);
            }
        }

        // Commit: il modello temporaneo entra nel dataset TDB2 solo se tutte le CQ sono valide
        Txn.executeWrite(knowledgeBase, () -> knowledgeBase.getDefaultModel().add(staging));

        return decomposition;
    }

    private void collectAxioms(
            OntModel ontology,
            OntModel queryModel,
            OntologicalDecomposition decomposition,
            AtomicInteger queryCounter
    ) {
        if (decomposition == null) {
            return;
        }

        if (decomposition.getTerminologies() != null) {
            decomposition.getTerminologies().forEach(terminology -> {
                OntClass subject = aiService.buildExpressionForConstruction(ontology, terminology.getSubjectConcept());
                OntClass object = aiService.buildExpressionForConstruction(ontology, terminology.getObjectConcept());
                switch (terminology.getRelationType()) {
                    case ConceptsRelation.ConceptRelationType.SUBCLASS_OF -> subject.addSuperClass(object);
                    case ConceptsRelation.ConceptRelationType.EQUIVALENT_TO,
                         ConceptsRelation.ConceptRelationType.EQUALS_TO -> subject.addEquivalentClass(object);
                }
            });
        }

        if (decomposition.getAssertions() != null) {
            decomposition.getAssertions().forEach(fact -> addAssertion(ontology, fact));
        }

        if (decomposition.getQueries() != null) {
            decomposition.getQueries().forEach(query -> {
                OntClass expression = aiService.buildExpressionForConstruction(queryModel, query);
                queryModel.createOntClass(ONTOLOGY_NAMESPACE + "QueryRequest" + queryCounter.incrementAndGet())
                        .addSuperClass(expression);
            });
        }
    }

    void addAssertion(OntModel ontology, AssertionFact fact) {
        if (fact.getType() == null) {
            throw new IllegalArgumentException("Assertion without type: " + fact);
        }
        switch (fact.getType()) {
            case CLASS_MEMBERSHIP -> {
                OntClass ontClass = ontology.createOntClass(
                        entityService.resolveClass(require(fact.getClassDescription(), fact), true));
                individual(ontology, require(fact.getIndividual(), fact)).attachClass(ontClass);
            }
            case OBJECT_PROPERTY -> {
                var property = ontology.createObjectProperty(
                        entityService.resolveProperty(require(fact.getProperty(), fact), true));
                OntIndividual subject = individual(ontology, require(fact.getSubject(), fact));
                OntIndividual object = individual(ontology, require(fact.getObject(), fact));
                subject.addAssertion(property, object);
            }
        }
    }

    private OntIndividual individual(OntModel ontology, String description) {
        return ontology.createIndividual(entityService.resolveIndividual(description, true));
    }

    private static String require(String value, AssertionFact fact) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Incomplete assertion: " + fact);
        }
        return value;
    }
}