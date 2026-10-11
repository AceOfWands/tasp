package com.github.aceofwands.tasp.controller.dto;

import java.util.List;

public record GenerateByCqRequest(
        String domainDescription,
        List<String> competencyQuestions
) {
}
