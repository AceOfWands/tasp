package com.github.aceofwands.tasp.component.toolSet;

import com.github.aceofwands.tasp.component.ExpressionBuilder;
import com.github.aceofwands.tasp.service.EntityService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntDataProperty;
import org.apache.jena.ontapi.model.OntIndividual;
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
public class ELUTools extends AbstractToolSet {
    private final EntityService entityService;
    private final OntModel model;
    private final boolean storeEntitiesIfNotFound;

    public ELUTools(
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

    @Tool("""
    Add the empty (Bottom) concept, representing no individual. It is a complete operand by itself.
    """)
    public String addBottomConceptToCurrentExpression() {
        return execute(() -> this.addExpression(model.getOWLNothing()));
    }

    @Tool("""
    Add a nominal: the class containing exactly one named individual, resolved from a natural language description.
    It is a complete operand by itself.
    Example: addNominalToCurrentExpression("the Gemelli teaching hospital in Rome")
    """)
    public String addNominalToCurrentExpression(@P("natural language description of the individual") String individualDescription) {
        return execute(() -> this.addExpression(model.createObjectOneOf(resolveToIndividual(individualDescription))));
    }

    @Tool("""
    Add an object property value restriction (property has value individual), equivalent to exists property . {individual}.
    It is a complete operand by itself.
    Example: addHasValueToCurrentExpression("an object property relating a person to the place where they work", "the Gemelli teaching hospital in Rome")
    """)
    public String addHasValueToCurrentExpression(
            @P("natural language description of the object property") String propertyLabel,
            @P("natural language description of the individual") String individualDescription) {
        return execute(() -> this.addExpression(model.createObjectHasValue(
                resolveToProperty(propertyLabel), resolveToIndividual(individualDescription))));
    }

    @Tool("""
    Add a self restriction (exists property . Self): the individuals related by the property to themselves.
    It is a complete operand by itself.
    Example: addHasSelfToCurrentExpression("an object property relating a person to the people they admire")
    """)
    public String addHasSelfToCurrentExpression(@P("natural language description of the object property") String propertyLabel) {
        return execute(() -> this.addExpression(model.createHasSelf(resolveToProperty(propertyLabel))));
    }

    @Tool("""
    Add an existential data property restriction (exists dataProperty . datatype).
    It is a complete operand by itself.
    The datatype must be one of: """ + ELDatatypes.NAMES + """
    .
    Example: addDataSomeValuesFromToCurrentExpression("a data property for the age of a person", "xsd:integer")
    """)
    public String addDataSomeValuesFromToCurrentExpression(
            @P("natural language description of the data property") String dataPropertyLabel,
            @P("datatype, as a prefixed name") String datatype) {
        return execute(() -> {
            final var range = ELDatatypes.resolve(model, datatype);
            this.addExpression(model.createDataSomeValuesFrom(resolveToDataProperty(dataPropertyLabel), range));
        });
    }

    @Tool("""
    Add a data property value restriction (dataProperty has value literal).
    It is a complete operand by itself.
    The datatype must be one of: """ + ELDatatypes.NAMES + """
    .
    Example: addDataHasValueToCurrentExpression("a data property for the name of a person", "Mario", "xsd:string")
    """)
    public String addDataHasValueToCurrentExpression(
            @P("natural language description of the data property") String dataPropertyLabel,
            @P("lexical form of the literal") String lexicalForm,
            @P("datatype, as a prefixed name") String datatype) {
        return execute(() -> {
            final var range = ELDatatypes.resolve(model, datatype);
            this.addExpression(model.createDataHasValue(
                    resolveToDataProperty(dataPropertyLabel),
                    model.createTypedLiteral(lexicalForm, range.getURI())));
        });
    }

    private static void requireAtLeastTwo(int operandCount) {
        if (operandCount < 2) {
            throw new IllegalArgumentException("operandCount must be at least 2, got " + operandCount);
        }
    }

    private OntObjectProperty resolveToProperty(String input) {
        return model.createObjectProperty(entityService.resolveProperty(input, storeEntitiesIfNotFound, true));
    }

    private OntDataProperty resolveToDataProperty(String input) {
        return model.createDataProperty(entityService.resolveDataProperty(input, storeEntitiesIfNotFound, true));
    }

    private OntIndividual resolveToIndividual(String input) {
        return model.createIndividual(entityService.resolveIndividual(input, storeEntitiesIfNotFound, true));
    }
}
