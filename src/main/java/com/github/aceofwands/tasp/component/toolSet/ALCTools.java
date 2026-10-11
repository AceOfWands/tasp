package com.github.aceofwands.tasp.component.toolSet;

import com.github.aceofwands.tasp.component.ExpressionBuilder;
import com.github.aceofwands.tasp.service.EntityService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.ontapi.model.OntObjectProperty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Tools are called in prefix order: an operator first, then its operands.
 * Operands are attached to the innermost operator that still needs them.
 * Expressions are created in the given model; an operator is only materialised when the final expression is collapsed.
 */
public class ALCTools extends AbstractToolSet {
    private final EntityService entityService;
    private final OntModel model;
    private final boolean storeEntitiesIfNotFound;

    public ALCTools(
            ExpressionBuilder expressionBuilder,
            EntityService entityService,
            OntModel model,
            boolean storeEntitiesIfNotFound
    ) {
        super(expressionBuilder);
        this.entityService = entityService;
        this.model = model;
        this.storeEntitiesIfNotFound = storeEntitiesIfNotFound;
    }

    @Tool("""
    Add a named OWL class resolved from a natural language description. It is a complete operand by itself.
    Example: addNamedClassToCurrentExpression("a medical professional trained to perform surgical operations")
    """)
    public String addNamedClassToCurrentExpression(@P("natural language description of the class") String classDescription) {
        return execute(() -> this.addExpression(
                model.createOntClass(entityService.resolveClass(classDescription, storeEntitiesIfNotFound, true))));
    }

    @Tool("""
    Add the universal (Top) concept, representing all individuals. It is a complete operand by itself.
    Use when an expression requires a filler but no specific class is intended.
    """)
    public String addTopConceptToCurrentExpression() {
        return execute(() -> this.addExpression(model.getOWLThing()));
    }

    @Tool("""
    Add a logical intersection (AND) that takes exactly 'operandCount' operands (at least 2).
    Call this BEFORE its operands, then add exactly 'operandCount' operands (each operand may itself be an operator with its own operands).
    The intersection closes automatically once all operands are given.
    """)
    public String addIntersectToCurrentExpression(@P("number of operands that follow") int operandCount) {
        return execute(() -> {
            requireAtLeastTwo(operandCount);
            this.addExpressionGenerator(operandCount, operands -> {
                List<OntClass> distinct = new ArrayList<>(new LinkedHashSet<>(operands));
                return distinct.size() == 1 ? distinct.getFirst() : model.createObjectIntersectionOf(distinct);
            });
        });
    }

    @Tool("""
    Add a logical union (OR) that takes exactly 'operandCount' operands (at least 2).
    Call this BEFORE its operands, then add exactly 'operandCount' operands.
    The union closes automatically once all operands are given.
    """)
    public String addUnionToCurrentExpression(@P("number of operands that follow") int operandCount) {
        return execute(() -> {
            requireAtLeastTwo(operandCount);
            this.addExpressionGenerator(operandCount, operands -> {
                List<OntClass> distinct = new ArrayList<>(new LinkedHashSet<>(operands));
                return distinct.size() == 1 ? distinct.getFirst() : model.createObjectUnionOf(distinct);
            });
        });
    }

    @Tool("""
    Add an existential property restriction (exists property . filler).
    The property is resolved by its natural language description.
    Call this BEFORE its filler, then add exactly ONE operand as the filler (use an intersection operator for several).
    Example: addExistsPropertyToCurrentExpression("an object property that relates an entity to the location where it operates or resides")
             then addNamedClassToCurrentExpression("a physical place used for medical treatment")
    """)
    public String addExistsPropertyToCurrentExpression(@P("natural language description of the property") String propertyLabel) {
        return execute(() -> {
            final OntObjectProperty property = resolveToProperty(propertyLabel);
            this.addExpressionGenerator(1, operands -> model.createObjectSomeValuesFrom(property, operands.getFirst()));
        });
    }

    @Tool("""
    Add one of the already-resolved sub-statements, identified by its 0-based position in the 'subStatements' list.
    It is a complete operand by itself.
    """)
    public String addPreloadedSubStatement(@P("0-based index in subStatements") int index) {
        return execute(() -> expressionBuilder.addPreloadedReference(index));
    }

    private static void requireAtLeastTwo(int operandCount) {
        if (operandCount < 2) {
            throw new IllegalArgumentException("operandCount must be at least 2, got " + operandCount);
        }
    }

    private OntObjectProperty resolveToProperty(String input) {
        return model.createObjectProperty(entityService.resolveProperty(input, storeEntitiesIfNotFound, true));
    }
}
