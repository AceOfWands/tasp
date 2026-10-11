package com.github.aceofwands.tasp.controller;

import com.github.aceofwands.tasp.controller.dto.GenerateByCqRequest;
import com.github.aceofwands.tasp.controller.dto.QueryRequest;
import com.github.aceofwands.tasp.model.OntologicalDecomposition;
import com.github.aceofwands.tasp.model.Request;
import com.github.aceofwands.tasp.service.RequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final RequestService requestService;

    @PostMapping("/message")
    public OntologicalDecomposition handleRequest(@Valid @RequestBody QueryRequest queryRequest) {
        Request request = new Request();
        request.setMessage(queryRequest.message());
        request.setAllowWrite(false);
        return requestService.handleRequest(request);
    }

    @PostMapping("/generate")
    public OntologicalDecomposition generateByCq(@Valid @RequestBody GenerateByCqRequest request) {
        return requestService.handleCqByQc(
                request.domainDescription(),
                request.competencyQuestions()
        );
    }
}
