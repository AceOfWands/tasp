package com.github.aceofwands.tasp.component;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.ontapi.model.OntObjectProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionBuilderTest {

    private static final String NS = "https://example.com#";

    private final OntModel model = OntModelFactory.createModel();
    private final OntClass doctor = model.createOntClass(NS + "Doctor");
    private final OntClass hospital = model.createOntClass(NS + "Hospital");
    private final OntClass city = model.createOntClass(NS + "City");
    private final OntObjectProperty worksIn = model.createObjectProperty(NS + "worksIn");
    private final OntObjectProperty locatedIn = model.createObjectProperty(NS + "locatedIn");

    private ExpressionBuilder builder() {
        return ExpressionBuilder.of(results -> {
            if (results.size() != 1) {
                throw new IncompleteExpressionException("expected one result, got " + results.size());
            }
            return results.getFirst();
        });
    }

    private void exists(ExpressionBuilder b, OntObjectProperty p) {
        b.addGenerator(1, args -> model.createObjectSomeValuesFrom(p, args.getFirst()));
    }

    private void intersect(ExpressionBuilder b, int n) {
        b.addGenerator(n, model::createObjectIntersectionOf);
    }

    static String show(OntClass c) {
        if (c instanceof OntClass.IntersectionOf i) {
            return i.components().map(ExpressionBuilderTest::show).collect(Collectors.joining(" & ", "(", ")"));
        }
        if (c instanceof OntClass.ObjectSomeValuesFrom r) {
            return "exists " + r.getProperty().getLocalName() + "." + show(r.getValue());
        }
        return c.getLocalName();
    }

    @Test
    void leafIsReturnedAsIs() {
        ExpressionBuilder b = builder();
        b.addExpression(doctor);
        assertThat(b.collapse()).isEqualTo(doctor);
    }

    @Test
    void existsNestsItsFiller() {
        ExpressionBuilder b = builder();
        exists(b, worksIn);
        b.addExpression(hospital);
        assertThat(show(b.collapse())).isEqualTo("exists worksIn.Hospital");
    }

    @Test
    void intersectionOfClassAndRestriction() {
        ExpressionBuilder b = builder();
        intersect(b, 2);
        b.addExpression(doctor);
        exists(b, worksIn);
        b.addExpression(hospital);

        assertThat(show(b.collapse())).isEqualTo("(Doctor & exists worksIn.Hospital)");
    }

    @Test
    void deepNestingClosesCascading() {
        ExpressionBuilder b = builder();
        exists(b, worksIn);
        intersect(b, 2);
        b.addExpression(hospital);
        exists(b, locatedIn);
        b.addExpression(city);

        assertThat(show(b.collapse())).isEqualTo("exists worksIn.(Hospital & exists locatedIn.City)");
    }

    @Test
    void incompleteNodeIsRejected() {
        ExpressionBuilder b = builder();
        intersect(b, 2);
        b.addExpression(doctor);
        assertThatThrownBy(b::collapse).isInstanceOf(IncompleteExpressionException.class);
    }

    @Test
    void secondRootIsRejectedByRootGenerator() {
        ExpressionBuilder b = builder();
        b.addExpression(doctor);
        b.addExpression(hospital);
        assertThatThrownBy(b::collapse).isInstanceOf(IncompleteExpressionException.class);
    }

    @Test
    void invalidArityIsRejected() {
        ExpressionBuilder b = builder();
        assertThatThrownBy(() -> b.addGenerator(0, _ -> doctor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preloadedExpressionsAreUsableAsOperands() {
        ExpressionBuilder b = builder();
        b.addPreloaded(doctor);
        b.addPreloaded(hospital);
        intersect(b, 2);
        b.addPreloadedReference(0);
        b.addPreloadedReference(1);
        assertThat(show(b.collapse())).isEqualTo("(Doctor & Hospital)");
    }

    @Test
    void singlePreloadedIsResultWhenNothingElseBuilt() {
        ExpressionBuilder b = builder();
        b.addPreloaded(List.of(doctor).getFirst());
        assertThat(b.collapse()).isEqualTo(doctor);
    }

    @Test
    void unknownPreloadedIndexIsRejected() {
        ExpressionBuilder b = builder();
        assertThatThrownBy(() -> b.addPreloadedReference(0)).isInstanceOf(IllegalArgumentException.class);
    }
}