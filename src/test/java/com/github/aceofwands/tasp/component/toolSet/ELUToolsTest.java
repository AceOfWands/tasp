package com.github.aceofwands.tasp.component.toolSet;

import com.github.aceofwands.tasp.component.ExpressionBuilder;
import com.github.aceofwands.tasp.service.EntityResolutionException;
import com.github.aceofwands.tasp.service.EntityService;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ELUToolsTest {

    private static final String NS = "https://example.com#";

    @Mock
    private EntityService entityService;

    private final OntModel model = OntModelFactory.createModel();

    private static String show(OntClass c) {
        if (c instanceof OntClass.IntersectionOf i) {
            return i.components().map(ELUToolsTest::show)
                    .collect(java.util.stream.Collectors.joining(" & ", "(", ")"));
        }
        if (c instanceof OntClass.ObjectSomeValuesFrom r) {
            return "exists " + r.getProperty().getLocalName() + "." + show(r.getValue());
        }
        return c.getLocalName();
    }

    private ExpressionBuilder newBuilder() {
        return ExpressionBuilder.of(List::getFirst);
    }

    @Test
    void addNamedClassUsesStoreModeWhenEnabled() {
        ExpressionBuilder builder = newBuilder();
        when(entityService.resolveClass("a generated class", true, true)).thenReturn(NS + "GeneratedClass");

        ELUTools tools = new ELUTools(builder, entityService, model, true);
        tools.addNamedClassToCurrentExpression("a generated class");

        assertThat(builder.collapse()).isEqualTo(model.createOntClass(NS + "GeneratedClass"));
        verify(entityService).resolveClass("a generated class", true, true);
    }

    @Test
    void prefixCallsBuildNestedIntersectionWithRestriction() {
        ExpressionBuilder builder = newBuilder();
        when(entityService.resolveClass("doctor", false, true)).thenReturn(NS + "Doctor");
        when(entityService.resolveClass("hospital", false, true)).thenReturn(NS + "Hospital");
        when(entityService.resolveProperty("works in", false, true)).thenReturn(NS + "worksIn");

        ELUTools tools = new ELUTools(builder, entityService, model, false);
        assertThat(tools.addIntersectToCurrentExpression(2)).isEqualTo("OK");
        tools.addNamedClassToCurrentExpression("doctor");
        tools.addExistsPropertyToCurrentExpression("works in");
        tools.addNamedClassToCurrentExpression("hospital");

        assertThat(show(builder.collapse())).isEqualTo("(Doctor & exists worksIn.Hospital)");
    }

    @Test
    void duplicateOperandsOfIntersectionAreMerged() {
        ExpressionBuilder builder = newBuilder();
        when(entityService.resolveClass(eq("doctor"), eq(false), eq(true))).thenReturn(NS + "Doctor");

        ELUTools tools = new ELUTools(builder, entityService, model, false);
        tools.addIntersectToCurrentExpression(2);
        tools.addNamedClassToCurrentExpression("doctor");
        tools.addNamedClassToCurrentExpression("doctor");

        assertThat(builder.collapse()).isEqualTo(model.createOntClass(NS + "Doctor"));
    }

    @Test
    void invalidOperandCountIsReportedAsErrorText() {
        ELUTools tools = new ELUTools(newBuilder(), entityService, model, false);

        assertThat(tools.addIntersectToCurrentExpression(1)).startsWith("ERROR");
    }

    @Test
    void addNamedClassUsesStrictModeWhenDisabled() {
        ExpressionBuilder builder = newBuilder();
        when(entityService.resolveClass("an existing class", false, true)).thenReturn(NS + "ExistingClass");

        ELUTools tools = new ELUTools(builder, entityService, model, false);
        tools.addNamedClassToCurrentExpression("an existing class");

        assertThat(builder.collapse()).isEqualTo(model.createOntClass(NS + "ExistingClass"));
        verify(entityService).resolveClass("an existing class", false, true);
    }

    @Test
    void ambiguousEntityIsHandedBackToTheAgentForRefinement() {
        ExpressionBuilder builder = newBuilder();
        when(entityService.resolveClass("a doctor", false, true)).thenThrow(new EntityResolutionException(
                "a doctor", List.of("a medical doctor")));

        ELUTools tools = new ELUTools(builder, entityService, model, false);

        assertThat(tools.addNamedClassToCurrentExpression("a doctor"))
                .startsWith("ERROR")
                .contains("REFINE")
                .contains("a medical doctor");
        org.assertj.core.api.Assertions.assertThatThrownBy(builder::collapse).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void bottomAndNominalAndHasValueAndHasSelfAreBuilt() {
        when(entityService.resolveIndividual("gemelli", false, true)).thenReturn(NS + "Gemelli");
        when(entityService.resolveProperty("works in", false, true)).thenReturn(NS + "worksIn");

        ExpressionBuilder b1 = newBuilder();
        new ELUTools(b1, entityService, model, false).addBottomConceptToCurrentExpression();
        assertThat(b1.collapse()).isEqualTo(model.getOWLNothing());

        ExpressionBuilder b2 = newBuilder();
        assertThat(new ELUTools(b2, entityService, model, false).addNominalToCurrentExpression("gemelli")).isEqualTo("OK");
        assertThat(b2.collapse()).isInstanceOf(OntClass.OneOf.class);

        ExpressionBuilder b3 = newBuilder();
        assertThat(new ELUTools(b3, entityService, model, false).addHasValueToCurrentExpression("works in", "gemelli")).isEqualTo("OK");
        assertThat(b3.collapse()).isInstanceOf(OntClass.ObjectHasValue.class);

        ExpressionBuilder b4 = newBuilder();
        assertThat(new ELUTools(b4, entityService, model, false).addHasSelfToCurrentExpression("works in")).isEqualTo("OK");
        assertThat(b4.collapse()).isInstanceOf(OntClass.HasSelf.class);
    }

    @Test
    void dataRestrictionsAreBuiltAndInvalidDatatypeIsRejected() {
        when(entityService.resolveDataProperty("age", false, true)).thenReturn(NS + "age");

        ExpressionBuilder b1 = newBuilder();
        ELUTools tools = new ELUTools(b1, entityService, model, false);
        assertThat(tools.addDataSomeValuesFromToCurrentExpression("age", "xsd:integer")).isEqualTo("OK");
        assertThat(b1.collapse()).isInstanceOf(OntClass.DataSomeValuesFrom.class);

        ExpressionBuilder b2 = newBuilder();
        tools = new ELUTools(b2, entityService, model, false);
        assertThat(tools.addDataHasValueToCurrentExpression("age", "42", "xsd:integer")).isEqualTo("OK");
        assertThat(b2.collapse()).isInstanceOf(OntClass.DataHasValue.class);

        assertThat(new ELUTools(newBuilder(), entityService, model, false)
                .addDataSomeValuesFromToCurrentExpression("age", "xsd:double")).startsWith("ERROR");
    }

}
