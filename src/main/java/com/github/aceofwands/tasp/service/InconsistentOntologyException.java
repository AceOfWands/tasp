package com.github.aceofwands.tasp.service;

import lombok.Getter;

import java.util.List;

@Getter
public class InconsistentOntologyException extends RuntimeException {
    private final List<String> problems;

    public InconsistentOntologyException(List<String> problems) {
        super("Ontology is inconsistent: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }
}