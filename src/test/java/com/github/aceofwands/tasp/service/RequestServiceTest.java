package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.ai.OntologicalDecompositionAiService;
import com.github.aceofwands.tasp.model.AssertionFact;
import com.github.aceofwands.tasp.model.OntologicalDecomposition;
import org.apache.jena.rdf.model.ResourceFactory;
import java.util.List;
import jakarta.persistence.EntityNotFoundException;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.Dataset;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    private static final String NS = "https://example.com#";

    @Mock
    private OntologicalDecompositionAiService decompositionAiService;
    @Mock
    private AiService aiService;
    @Mock
    private EntityService entityService;

    private final OntModel ontology = OntModelFactory.createModel();
    private final Dataset knowledgeBase = TDB2Factory.createDataset();
    private RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(decompositionAiService, aiService, entityService, knowledgeBase, new OntologyValidator());
    }

    @Test
    void classMembershipKeepsIndividualAndClass() {
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveIndividual("Mario", true)).thenReturn(NS + "Mario");

        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual("Mario");
        fact.setClassDescription("Surgeon");
        requestService.addAssertion(ontology, fact);

        assertThat(ontology.contains(
                ontology.createResource(NS + "Mario"), RDF.type, ontology.createResource(NS + "Surgeon"))).isTrue();
    }

    @Test
    void objectPropertyRelatesSubjectToObject() {
        when(entityService.resolveIndividual("Rossi", true)).thenReturn(NS + "Rossi");
        when(entityService.resolveIndividual("Gemelli", true)).thenReturn(NS + "Gemelli");
        when(entityService.resolveProperty("works in", true)).thenReturn(NS + "worksIn");

        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.OBJECT_PROPERTY);
        fact.setSubject("Rossi");
        fact.setProperty("works in");
        fact.setObject("Gemelli");
        requestService.addAssertion(ontology, fact);

        assertThat(ontology.contains(
                ontology.createResource(NS + "Rossi"),
                ontology.createProperty(NS + "worksIn"),
                ontology.createResource(NS + "Gemelli"))).isTrue();
        assertThat(ontology.contains(
                ontology.createResource(NS + "Gemelli"),
                ontology.createProperty(NS + "worksIn"),
                ontology.createResource(NS + "Rossi"))).isFalse();
    }

    @Test
    void incompleteFactIsRejected() {
        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual("Mario");

        assertThatThrownBy(() -> requestService.addAssertion(ontology, fact))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingTypeIsRejected() {
        assertThatThrownBy(() -> requestService.addAssertion(ontology, new AssertionFact()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groundingFailureIsPropagated() {
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveIndividual("Unknown", true))
                .thenThrow(new EntityNotFoundException("Could not ground entity: Unknown"));

        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual("Unknown");
        fact.setClassDescription("Surgeon");

        assertThatThrownBy(() -> requestService.addAssertion(ontology, fact))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void successfulRequestIsCommittedToKnowledgeBase() {
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveIndividual("Mario", true)).thenReturn(NS + "Mario");
        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual("Mario");
        fact.setClassDescription("Surgeon");
        OntologicalDecomposition decomposition = new OntologicalDecomposition();
        decomposition.setAssertions(List.of(fact));
        when(decompositionAiService.importDomain("domain", "q")).thenReturn(decomposition);

        requestService.handleCqByQc("domain", List.of("q"));

        boolean committed = Txn.calculateRead(knowledgeBase, () -> knowledgeBase.getDefaultModel().contains(
                ResourceFactory.createResource(NS + "Mario"), RDF.type, ResourceFactory.createResource(NS + "Surgeon")));
        assertThat(committed).isTrue();
    }

    @Test
    void failedRequestLeavesKnowledgeBaseUntouched() {
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveIndividual("Mario", true)).thenReturn(NS + "Mario");
        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual("Mario");
        fact.setClassDescription("Surgeon");
        OntologicalDecomposition decomposition = new OntologicalDecomposition();
        decomposition.setAssertions(List.of(fact));
        when(decompositionAiService.importDomain("domain", "q1")).thenReturn(decomposition);
        when(decompositionAiService.importDomain("domain", "q2")).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> requestService.handleCqByQc("domain", List.of("q1", "q2")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(Txn.calculateRead(knowledgeBase, () -> knowledgeBase.getDefaultModel().isEmpty())).isTrue();
    }
    @Test
    void inconsistentStagingIsRejectedAndNotCommitted() {
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveIndividual("Mario", true)).thenReturn(NS + "Mario");
        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual("Mario");
        fact.setClassDescription("Surgeon");
        OntologicalDecomposition decomposition = new OntologicalDecomposition();
        decomposition.setAssertions(List.of(fact));
        when(decompositionAiService.importDomain("domain", "q")).thenReturn(decomposition);

        // KB preesistente: Surgeon è disgiunta da Mario's class Person-like "Robot"; Mario è già Robot
        Txn.executeWrite(knowledgeBase, () -> {
            var kb = knowledgeBase.getDefaultModel();
            var surgeon = kb.createResource(NS + "Surgeon");
            var robot = kb.createResource(NS + "Robot");
            surgeon.addProperty(RDF.type, org.apache.jena.vocabulary.OWL.Class);
            robot.addProperty(RDF.type, org.apache.jena.vocabulary.OWL.Class);
            surgeon.addProperty(org.apache.jena.vocabulary.OWL.disjointWith, robot);
            kb.createResource(NS + "Mario").addProperty(RDF.type, robot);
        });

        assertThatThrownBy(() -> requestService.handleCqByQc("domain", List.of("q")))
                .isInstanceOf(InconsistentOntologyException.class);

        boolean committed = Txn.calculateRead(knowledgeBase, () -> knowledgeBase.getDefaultModel().contains(
                ResourceFactory.createResource(NS + "Mario"), RDF.type, ResourceFactory.createResource(NS + "Surgeon")));
        assertThat(committed).isFalse();
    }
    private OntologicalDecomposition membership(String individual, String className) {
        AssertionFact fact = new AssertionFact();
        fact.setType(AssertionFact.FactType.CLASS_MEMBERSHIP);
        fact.setIndividual(individual);
        fact.setClassDescription(className);
        OntologicalDecomposition decomposition = new OntologicalDecomposition();
        decomposition.setAssertions(List.of(fact));
        return decomposition;
    }

    private void seedDisjointClasses() {
        Txn.executeWrite(knowledgeBase, () -> {
            var kb = knowledgeBase.getDefaultModel();
            var surgeon = kb.createResource(NS + "Surgeon");
            var robot = kb.createResource(NS + "Robot");
            surgeon.addProperty(RDF.type, org.apache.jena.vocabulary.OWL.Class);
            robot.addProperty(RDF.type, org.apache.jena.vocabulary.OWL.Class);
            surgeon.addProperty(org.apache.jena.vocabulary.OWL.disjointWith, robot);
        });
    }

    @Test
    void inconsistentSecondCqRejectsAllCqs() {
        seedDisjointClasses();
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveClass("Robot", true)).thenReturn(NS + "Robot");
        when(entityService.resolveIndividual("Mario", true)).thenReturn(NS + "Mario");
        when(decompositionAiService.importDomain("domain", "q1")).thenReturn(membership("Mario", "Surgeon"));
        when(decompositionAiService.importDomain("domain", "q2")).thenReturn(membership("Mario", "Robot"));

        assertThatThrownBy(() -> requestService.handleCqByQc("domain", List.of("q1", "q2")))
                .isInstanceOf(InconsistentOntologyException.class);

        boolean anyCommitted = Txn.calculateRead(knowledgeBase, () -> knowledgeBase.getDefaultModel()
                .contains(ResourceFactory.createResource(NS + "Mario"), null, (org.apache.jena.rdf.model.RDFNode) null));
        assertThat(anyCommitted).isFalse();
    }

    @Test
    void inconsistentFirstCqStopsBeforeProcessingTheNext() {
        seedDisjointClasses();
        Txn.executeWrite(knowledgeBase, () -> knowledgeBase.getDefaultModel()
                .createResource(NS + "Mario").addProperty(RDF.type, ResourceFactory.createResource(NS + "Robot")));
        when(entityService.resolveClass("Surgeon", true)).thenReturn(NS + "Surgeon");
        when(entityService.resolveIndividual("Mario", true)).thenReturn(NS + "Mario");
        when(decompositionAiService.importDomain("domain", "q1")).thenReturn(membership("Mario", "Surgeon"));

        assertThatThrownBy(() -> requestService.handleCqByQc("domain", List.of("q1", "q2")))
                .isInstanceOf(InconsistentOntologyException.class);

        verify(decompositionAiService, never()).importDomain("domain", "q2");
    }}
