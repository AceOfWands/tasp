package com.github.aceofwands.tasp.component.toolSet;

import org.apache.jena.ontapi.model.OntDataRange;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Datatypes allowed by the OWL 2 EL profile, addressable by prefixed name. */
final class ELDatatypes {
    /** A compile-time constant, so that it can be used in tool descriptions. */
    static final String NAMES = "rdfs:Literal, rdf:PlainLiteral, rdf:XMLLiteral, xsd:decimal, xsd:integer, "
            + "xsd:nonNegativeInteger, xsd:string, xsd:normalizedString, xsd:token, xsd:Name, xsd:NCName, "
            + "xsd:NMTOKEN, xsd:hexBinary, xsd:base64Binary, xsd:anyURI, xsd:dateTime, xsd:dateTimeStamp";

    private static final Set<String> ALLOWED = Arrays.stream(NAMES.split(", ")).collect(Collectors.toSet());

    private ELDatatypes() {
    }

    static OntDataRange.Named resolve(OntModel model, String name) {
        String key = name == null ? "" : name.strip();
        if (!ALLOWED.contains(key)) {
            throw new IllegalArgumentException("datatype '" + name + "' is not allowed, use one of: " + NAMES);
        }
        return model.getDatatype(toIri(key));
    }

    private static String toIri(String prefixedName) {
        String local = prefixedName.substring(prefixedName.indexOf(':') + 1);
        return switch (prefixedName.substring(0, prefixedName.indexOf(':'))) {
            case "xsd" -> XSD.getURI() + local;
            case "rdf" -> RDF.getURI() + local;
            default -> RDFS.getURI() + local;
        };
    }
}
