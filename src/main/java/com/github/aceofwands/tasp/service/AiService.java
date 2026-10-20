package com.github.aceofwands.tasp.service;

import com.github.aceofwands.tasp.ai.FormulaBuilderAiService;
import com.github.aceofwands.tasp.component.ExpressionBuilder;
import com.github.aceofwands.tasp.component.IncompleteExpressionException;
import com.github.aceofwands.tasp.component.toolSet.ELUTools;
import com.github.aceofwands.tasp.model.Statement;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {
    private static final int MAX_BUILD_ATTEMPTS = 3;

    private final ChatModel model;
    private final EntityService entityService;

    public OntClass buildExpressionForQuery(OntModel ontology, Statement statement) {
        return buildExpression(ontology, statement, false);
    }

    public OntClass buildExpressionForConstruction(OntModel ontology, Statement statement) {
        return buildExpression(ontology, statement, true);
    }

    private OntClass buildExpression(OntModel ontology, Statement statement, boolean storeEntitiesIfNotFound) {
        List<OntClass> children = statement.getSubStatements() == null
                ? List.of()
                : statement.getSubStatements().stream()
                        .map(child -> buildExpression(ontology, child, storeEntitiesIfNotFound))
                        .toList();

        Statement current = statement;
        IncompleteExpressionException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_BUILD_ATTEMPTS; attempt++) {
            ExpressionBuilder builder = ExpressionBuilder.of(this::selectRootExpression);
            children.forEach(builder::addPreloaded);

            FormulaBuilderAiService ai = AiServices.builder(FormulaBuilderAiService.class)
                    .chatModel(model)
                    .tools(new ELUTools(builder, entityService, ontology, storeEntitiesIfNotFound))
                    .build();
            ai.buildExpression(current);

            try {
                return builder.collapse();
            } catch (IncompleteExpressionException e) {
                lastFailure = e;
                log.warn("Expression build attempt {}/{} failed: {}", attempt, MAX_BUILD_ATTEMPTS, e.getMessage());
                current = withFeedback(statement, e.getMessage());
            }
        }
        throw new IllegalStateException(
                "Could not build a valid expression for: " + statement.getDescription(), lastFailure);
    }

    private OntClass selectRootExpression(List<OntClass> results) {
        if (results.size() != 1) {
            throw new IncompleteExpressionException(
                    "The final expression must have exactly one root, but %d were produced.".formatted(results.size()));
        }
        return results.getFirst();
    }

    private Statement withFeedback(Statement original, String error) {
        Statement retry = new Statement();
        retry.setDescription(original.getDescription()
                + "\n[Previous attempt failed: " + error
                + " Build exactly one expression: call each operator BEFORE its operands and give exactly the declared number of operands.]");
        retry.setSubStatements(original.getSubStatements());
        return retry;
    }
}
