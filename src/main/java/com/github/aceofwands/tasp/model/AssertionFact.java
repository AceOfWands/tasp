package com.github.aceofwands.tasp.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Data
public class AssertionFact {
    public enum FactType {
        CLASS_MEMBERSHIP, OBJECT_PROPERTY
    }

    @Description("CLASS_MEMBERSHIP when an individual belongs to a class ('Mario is a surgeon'); OBJECT_PROPERTY when two individuals are related ('Dr. Rossi works at Gemelli Hospital').")
    private FactType type;

    @Description("CLASS_MEMBERSHIP only: the specific individual, with a short encyclopedic description. Example: 'Mario, a fictional Italian man'.")
    private String individual;

    @Description("CLASS_MEMBERSHIP only: the class the individual belongs to, as a short encyclopedic description. Example: 'a medical professional trained to perform surgical operations'. It must be a single class, not a composite description.")
    private String classDescription;

    @Description("OBJECT_PROPERTY only: the subject individual, with a short encyclopedic description.")
    private String subject;

    @Description("OBJECT_PROPERTY only: the relation between subject and object, described as an object property. Example: 'an object property relating a person to the place where they work'.")
    private String property;

    @Description("OBJECT_PROPERTY only: the object individual, with a short encyclopedic description.")
    private String object;
}
