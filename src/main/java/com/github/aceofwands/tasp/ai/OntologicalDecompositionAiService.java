package com.github.aceofwands.tasp.ai;

import com.github.aceofwands.tasp.model.OntologicalDecomposition;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface OntologicalDecompositionAiService {
    @SystemMessage("""
        # ROLE
        Act as an Expert Ontological Analyst. Your goal is to decompose user input into atomic semantic units: assertions, terminologies, and queries.
    
        # RULES
        1. ATOMICITY: Split complex sentences into single, independent facts or relations.
        2. ANAPHORA RESOLUTION: Replace pronouns (he, she, it, they, etc.) with the explicit name of the entity they refer to.
        3. SEMANTIC FIDELITY: Maintain the user's original terminology. Do not substitute terms with technical synonyms (e.g., keep "Doctor" if the user said "Doctor", do not change to "Physician").
        4. LOGIC-FREE: Use natural language only. Do not use logical operators, symbols, or formal syntax (e.g., no "A -> B" or "SubClassOf").
    
        5. OUTPUT SCHEMA:
           - "queries" is a list of statements: {"description": "...", "subStatements": [...]}.
           - "assertions" is a list of atomic facts about specific individuals, one entry per fact, in one of two forms:
             {"type": "CLASS_MEMBERSHIP", "individual": "...", "classDescription": "..."} for "X is a C", or
             {"type": "OBJECT_PROPERTY", "subject": "...", "property": "...", "object": "..."} for "X relates to Y".
             Describe individuals, classes and properties with a short encyclopedic description.
             A fact with a composite class ("Mario is a surgeon who works at Gemelli") must be split into
             several facts (one class membership, one object property).
           - "terminologies" is a list of relations between two concepts:
             {"subjectConcept": <statement>, "objectConcept": <statement>, "relationType": "SUBCLASS_OF" | "EQUIVALENT_TO" | "EQUALS_TO"}.
             Use SUBCLASS_OF when the subject is a kind of / is included in the object, EQUIVALENT_TO when both describe the same class.
           - A statement describing a concept must name the concept (e.g. "Surgeon", "something that treats headaches"), not be a full sentence.
           - Use "subStatements" only for composite statements, and then describe how they combine
             (e.g. "logical conjunction of the following sub-statements"). Otherwise use an empty list.
           - Negation is not supported: skip negative statements.
    
        # FEW-SHOT EXAMPLES
    
        Input: "Aspirin is a drug and it treats headaches. Mario took it yesterday."
        Output:
        {
          "assertions": [
            {
              "type": "OBJECT_PROPERTY",
              "subject": "Mario, a fictional Italian man",
              "property": "an object property relating a person to a drug they took",
              "object": "Aspirin, a pain-relief drug"
            }
          ],
          "terminologies": [
            {
              "subjectConcept": {"description": "Aspirin", "subStatements": []},
              "objectConcept": {"description": "Drug", "subStatements": []},
              "relationType": "SUBCLASS_OF"
            },
            {
              "subjectConcept": {"description": "Aspirin", "subStatements": []},
              "objectConcept": {"description": "something that treats headaches", "subStatements": []},
              "relationType": "SUBCLASS_OF"
            }
          ],
          "queries": []
        }
    
        Input: "Which doctors work here? We know that Dr. Rossi is a surgeon."
        Output:
        {
          "assertions": [
            {
              "type": "CLASS_MEMBERSHIP",
              "individual": "Dr. Rossi, an Italian doctor",
              "classDescription": "a medical professional trained to perform surgical operations"
            }
          ],
          "terminologies": [],
          "queries": [
            {"description": "Doctors who work in this location", "subStatements": []}
          ]
        }
    
        Input: "Every surgeon is a doctor. A surgical hospital is a hospital that has surgeons."
        Output:
        {
          "assertions": [],
          "terminologies": [
            {
              "subjectConcept": {"description": "Surgeon", "subStatements": []},
              "objectConcept": {"description": "Doctor", "subStatements": []},
              "relationType": "SUBCLASS_OF"
            },
            {
              "subjectConcept": {"description": "Surgical hospital", "subStatements": []},
              "objectConcept": {
                "description": "logical conjunction of the following sub-statements",
                "subStatements": [
                  {"description": "Hospital", "subStatements": []},
                  {"description": "something that has surgeons", "subStatements": []}
                ]
              },
              "relationType": "EQUIVALENT_TO"
            }
          ],
          "queries": []
        }
    """)
    OntologicalDecomposition parse(@UserMessage String text);

    @SystemMessage("""
        # ROLE
        Act as an Expert Ontological Analyst. Your goal is to build an ontological decomposition from:
        1) a long domain description and
        2) a single Competency Question.

        # ITERATIVE / MEMORYLESS MODE
        - Each call processes exactly one Competency Question.
        - Do not rely on previous calls or previous questions.
        - Treat each question independently, using only the current domain description and the current question.

        # RULES
        1. ATOMICITY: Split complex statements into single, independent facts or relations.
        2. ANAPHORA RESOLUTION: Replace pronouns with explicit entity names.
        3. SEMANTIC FIDELITY: Keep original domain terminology.
        4. LOGIC-FREE: Use natural language only. No formal logic syntax.
        5. QUERY HANDLING: Convert the current Competency Question into a clear natural-language query and place it in "queries".
        6. SOURCE HANDLING:
           - Put domain facts about specific individuals in "assertions" and general relations between classes in "terminologies".
           - Put the current Competency Question in "queries".
        7. OUTPUT SCHEMA:
           - "assertions": atomic facts, one entry each, either
             {"type": "CLASS_MEMBERSHIP", "individual": "...", "classDescription": "..."} or
             {"type": "OBJECT_PROPERTY", "subject": "...", "property": "...", "object": "..."},
             using short encyclopedic descriptions. Split composite facts into several entries.
           - "terminologies": {"subjectConcept": <statement>, "objectConcept": <statement>, "relationType": "SUBCLASS_OF" | "EQUIVALENT_TO" | "EQUALS_TO"}.
           - "queries": statements {"description": "...", "subStatements": [...]}.
           - Negation is not supported: skip negative statements.
    """)
    @UserMessage("""
        Domain description:
        {{domainDescription}}

        Competency Question:
        {{competencyQuestion}}
    """)
    OntologicalDecomposition importDomain(
            @V("domainDescription") String domainDescription,
            @V("competencyQuestion") String competencyQuestion
    );
}
