package com.github.aceofwands.tasp.component.toolSet;

import com.github.aceofwands.tasp.component.ExpressionBuilder;
import lombok.RequiredArgsConstructor;
import org.apache.jena.ontapi.model.OntClass;

@RequiredArgsConstructor
public abstract class AbstractToolSet implements IToolSet {
    protected final ExpressionBuilder expressionBuilder;

    protected void addExpression(OntClass expression) {
        expressionBuilder.addExpression(expression);
    }

    protected void addExpressionGenerator(int arity, ExpressionBuilder.MergeStatementGenerator expressionGenerator) {
        expressionBuilder.addGenerator(arity, expressionGenerator);
    }

    /** Runs a tool action and reports the outcome to the LLM as text, so that it can correct itself. */
    protected String execute(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (RuntimeException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
