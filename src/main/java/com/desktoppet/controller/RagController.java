package com.desktoppet.controller;

import com.desktoppet.controller.dto.ApiModels.RagDebugHitResponse;
import com.desktoppet.controller.dto.ApiModels.RagDebugRequest;
import com.desktoppet.controller.dto.ApiModels.RagDebugResponse;
import com.desktoppet.controller.dto.ApiModels.RagReindexResponse;
import com.desktoppet.rag.RagIndexingResult;
import com.desktoppet.rag.RagDebugHit;
import com.desktoppet.rag.RedisRagService;
import com.desktoppet.rag.RagService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PostMapping("/debug")
    public RagDebugResponse debug(@RequestBody RagDebugRequest request) {
        String query = request == null || request.query() == null ? "" : request.query();
        return new RagDebugResponse(query, toResponse(ragService.debugRetrieve(query)));
    }

    @PostMapping("/debug/raw-candidates")
    public RagDebugResponse debugRawCandidates(@RequestBody RagDebugRequest request) {
        String query = request == null || request.query() == null ? "" : request.query();
        if (ragService instanceof RedisRagService redisRagService) {
            return new RagDebugResponse(query, toResponse(redisRagService.debugRetrieveRawCandidates(query)));
        }
        return new RagDebugResponse(query, toResponse(ragService.debugRetrieve(query)));
    }

    private List<RagDebugHitResponse> toResponse(List<RagDebugHit> hits) {
        return hits.stream()
                .map(hit -> new RagDebugHitResponse(
                        hit.rank(),
                        hit.source(),
                        hit.parentId(),
                        hit.vectorScore(),
                        hit.dialogueBoost(),
                        hit.weightedScore(),
                        hit.boostReason(),
                        hit.textLength(),
                        hit.text()
                ))
                .toList();
    }
}
