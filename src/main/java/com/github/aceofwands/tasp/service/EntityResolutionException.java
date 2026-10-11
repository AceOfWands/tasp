package com.github.aceofwands.tasp.service;

import jakarta.persistence.EntityNotFoundException;

import java.util.List;

/**
 * The description is ambiguous: a candidate stood out but not confidently enough. The message asks the calling agent,
 * which knows the context of the description, to reformulate it.
 */
public class EntityResolutionException extends EntityNotFoundException {

    public EntityResolutionException(String description, List<String> candidates) {
        super("The description '" + description + "' is too vague to be matched to an existing entity."
                + " Nearest candidates: " + String.join(" | ", candidates) + "."
                + " Suggested action REFINE: call the same tool again with a more specific description.");
    }
}