package com.desktoppet.controller;

import com.desktoppet.controller.dto.ApiModels.RagReindexResponse;
import com.desktoppet.rag.RagIndexingResult;
import com.desktoppet.rag.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/reindex")
    public RagReindexResponse reindex() {
        RagIndexingResult result = ragService.reindexPackagedDocuments();
        return new RagReindexResponse(result.indexedDocuments(), result.skippedDocuments(), result.messages());
    }
}
