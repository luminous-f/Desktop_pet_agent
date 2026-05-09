package com.desktoppet.service.impl;

import com.desktoppet.config.AppConfig;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.service.FileService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileServiceImpl implements FileService {
    private final FileOrganizer fileOrganizer;

    public FileServiceImpl(AppConfig config) {
        this.fileOrganizer = new FileOrganizer(config);
    }

    @Override
    public FileOrganizer.AllowedRootsResult allowedRoots() {
        return new FileOrganizer.AllowedRootsResult(fileOrganizer.allowedRoots());
    }

    @Override
    public FileOrganizer.AllowedRootsResult replaceAllowedRoots(List<String> roots) {
        return fileOrganizer.replaceAllowedRoots(roots);
    }

    @Override
    public FileOrganizer.PreviewResult preview(FileOrganizer.PreviewRequest request) {
        return fileOrganizer.preview(request);
    }

    @Override
    public FileOrganizer.ConfirmResult confirm(String previewId) {
        return fileOrganizer.confirm(previewId);
    }

    @Override
    public String summarizePreview(FileOrganizer.PreviewResult preview) {
        return fileOrganizer.summarizePreview(preview);
    }

    @Override
    public String summarizeConfirm(FileOrganizer.ConfirmResult result) {
        return fileOrganizer.summarizeConfirm(result);
    }

    @Override
    public String summarizeAllowedRoots(FileOrganizer.AllowedRootsResult result) {
        return fileOrganizer.summarizeAllowedRoots(result);
    }
}
