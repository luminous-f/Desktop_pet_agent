package com.desktoppet.service;

import com.desktoppet.files.FileOrganizer;

import java.util.List;

public interface FileService {
    FileOrganizer.AllowedRootsResult allowedRoots();

    FileOrganizer.AllowedRootsResult replaceAllowedRoots(List<String> roots);

    FileOrganizer.PreviewResult preview(FileOrganizer.PreviewRequest request);

    FileOrganizer.ConfirmResult confirm(String previewId);

    String summarizePreview(FileOrganizer.PreviewResult preview);

    String summarizeConfirm(FileOrganizer.ConfirmResult result);

    String summarizeAllowedRoots(FileOrganizer.AllowedRootsResult result);
}
