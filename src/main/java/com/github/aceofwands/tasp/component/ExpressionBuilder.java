package com.github.aceofwands.tasp.component;

import lombok.RequiredArgsConstructor;
import org.apache.jena.ontapi.model.OntClass;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds a class expression tree from a prefix-ordered sequence of calls.
 * Every node declares how many operands it needs (its arity); operands are attached to the
 * innermost node still waiting for them, and a node is closed automatically once complete.
 * <pre>
 *   intersect(2), Doctor, exists(worksIn), Hospital   ->   Doctor ⊓ ∃worksIn.Hospital
 * </pre>
 */
@RequiredArgsConstructor(staticName = "of")
public class ExpressionBuilder {

    @FunctionalInterface
    public interface MergeStatementGenerator {
        OntClass generate(List<OntClass> operands);
    }

    private static final class Node {
        private final int arity;
        private final MergeStatementGenerator generator;
        private final List<Node> children = new ArrayList<>();

        private Node(int arity, MergeStatementGenerator generator) {
            this.arity = arity;
            this.generator = generator;
        }

        private boolean isComplete() {
            return children.size() == arity;
        }

        private OntClass evaluate() {
            return generator.generate(children.stream().map(Node::evaluate).toList());
        }
    }

    /** Merges the completed top-level expressions into the final result. */
    private final MergeStatementGenerator rootGenerator;
    private final List<Node> roots = new ArrayList<>();
    private final Deque<Node> open = new ArrayDeque<>();
    private final List<OntClass> preloaded = new ArrayList<>();

    public synchronized OntClass collapse() {
        if (!open.isEmpty()) {
            Node pending = open.peek();
            throw new IncompleteExpressionException(
                    "Expression is incomplete: an operator still needs %d more operand(s)."
                            .formatted(pending.arity - pending.children.size()));
        }
        List<OntClass> results = roots.isEmpty()
                ? List.copyOf(preloaded)
                : roots.stream().map(Node::evaluate).toList();
        return rootGenerator.generate(results);
    }

    public synchronized void addExpression(OntClass expression) {
        attach(new Node(0, ignored -> expression));
    }

    /** Adds an operator that expects exactly {@code arity} operands to follow. */
    public synchronized void addGenerator(int arity, MergeStatementGenerator generator) {
        if (arity < 1) {
            throw new IllegalArgumentException("An operator needs at least one operand, got " + arity);
        }
        attach(new Node(arity, generator));
    }

    /** Registers an already-built expression, referenceable by its index via {@link #addPreloadedReference}. */
    public synchronized void addPreloaded(OntClass expression) {
        preloaded.add(expression);
    }

    public synchronized int preloadedCount() {
        return preloaded.size();
    }

    public synchronized void addPreloadedReference(int index) {
        if (index < 0 || index >= preloaded.size()) {
            throw new IllegalArgumentException(
                    "No pre-loaded sub-expression at index %d (available: %d)".formatted(index, preloaded.size()));
        }
        addExpression(preloaded.get(index));
    }

    private void attach(Node node) {
        Node parent = open.peek();
        if (parent == null) {
            roots.add(node);
        } else {
            parent.children.add(node);
        }
        if (node.arity > 0) {
            open.push(node);
        } else {
            closeCompleted();
        }
    }

    private void closeCompleted() {
        while (!open.isEmpty() && open.peek().isComplete()) {
            open.pop();
        }
    }
}
