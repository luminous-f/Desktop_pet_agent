package com.desktoppet.rag;

import java.util.List;

public interface RagService {
    List<String> retrieve(String query);

    void upsertDocument(String id, String text);

    RagIndexingResult reindexPackagedDocuments();
}
