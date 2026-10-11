package com.github.aceofwands.tasp.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

import java.util.List;

@Data
public class Statement {
    @Description("A description of the statement, which can be a fact, a definition, a question, or any other type of information. Example: 'Mario is a surgeon.'. If the statement is complex and contains sub-statements, the description should provide a description of the relation between the sub-statements, such as 'logical conjunction of the following sub-statements'.")
    private String description;
    @Description("A list of sub-statements that are part of this statement. This field is optional and can be used to represent complex statements that are composed of multiple simpler statements. If the statement is simple and does not contain any sub-statements, this field can be left empty or null.")
    private List<Statement> subStatements;
}
