package com.desktoppet.rag;

import java.util.List;

public interface RagService {
    List<String> retrieve(String query);

    default List<RagDebugHit> debugRetrieve(String query) {
        List<String> references = retrieve(query);
        java.util.ArrayList<RagDebugHit> hits = new java.util.ArrayList<>();
        for (int i = 0; i < references.size(); i++) {
            String text = references.get(i);
            hits.add(new RagDebugHit(
                    i + 1,
                    "unknown",
                    "unknown",
                    "unknown",
                    0.0,
                    0.0,
                    "debug details unavailable",
                    text == null ? 0 : text.length(),
                    text
            ));
        }
        return List.copyOf(hits);
    }

    void upsertDocument(String id, String text);

    RagIndexingResult reindexPackagedDocuments();
}
