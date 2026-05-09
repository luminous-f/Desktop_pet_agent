package com.desktoppet.rag;

import java.util.List;

public record RagIndexingResult(int indexedDocuments, int skippedDocuments, List<String> messages) {
}
