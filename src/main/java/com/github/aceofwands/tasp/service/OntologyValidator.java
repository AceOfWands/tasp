package com.github.aceofwands.tasp.service;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.reasoner.ValidityReport;
import org.apache.jena.system.Txn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OntologyValidator {

    /** Checks that the knowledge base merged with the staged axioms has no OWL inconsistency. */
    public void validate(Dataset knowledgeBase, Model staging) {
        Model merged = ModelFactory.createDefaultModel();
        Txn.executeRead(knowledgeBase, () -> merged.add(knowledgeBase.getDefaultModel()));
        merged.add(staging);

        InfModel inferred = ModelFactory.createInfModel(ReasonerRegistry.getOWLMicroReasoner(), merged);
        ValidityReport report = inferred.validate();
        if (!report.isValid()) {
            List<String> problems = new ArrayList<>();
            report.getReports().forEachRemaining(r -> {
                if (r.isError()) {
                    problems.add(r.toString());
                }
            });
            throw new InconsistentOntologyException(problems);
        }
    }
}