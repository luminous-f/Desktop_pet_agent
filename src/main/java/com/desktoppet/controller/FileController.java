package com.desktoppet.controller;

import com.desktoppet.common.AppException;
import com.desktoppet.controller.dto.ApiModels.AllowedRootsRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizeConfirmRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizePreviewRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizeRequest;
import com.desktoppet.controller.dto.ApiModels.FileOrganizeResponse;
import com.desktoppet.files.FileOrganizer;
import com.desktoppet.service.FileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/allowed-roots")
    public FileOrganizer.AllowedRootsResult allowedRoots() {
        return fileService.allowedRoots();
    }

    @PutMapping("/allowed-roots")
    public FileOrganizer.AllowedRootsResult replaceAllowedRoots(@RequestBody AllowedRootsRequest request) {
        if (request.roots() == null) {
            throw AppException.badRequest("roots 不能为空");
        }
        return fileService.replaceAllowedRoots(request.roots());
    }

    @PostMapping("/organize")
    public FileOrganizeResponse organizeFiles(@RequestBody FileOrganizeRequest request) {
        if (request.sourceRoot() == null || request.sourceRoot().isBlank()) {
            throw AppException.badRequest("sourceRoot 不能为空");
        }
        if (request.extension() == null || request.extension().isBlank()) {
            throw AppException.badRequest("extension 不能为空");
        }
        FileOrganizer.PreviewResult preview = fileService.preview(
                new FileOrganizer.PreviewRequest(request.sourceRoot(), request.extension(), "按主题分类文献")
        );
        FileOrganizer.ConfirmResult result = fileService.confirm(preview.previewId());
        return new FileOrganizeResponse(fileService.summarizeConfirm(result));
    }

    @PostMapping("/organize/preview")
    public FileOrganizer.PreviewResult previewOrganizeFiles(@RequestBody FileOrganizePreviewRequest request) {
        if (request.sourceRoot() == null || request.sourceRoot().isBlank()) {
            throw AppException.badRequest("sourceRoot 不能为空");
        }
        return fileService.preview(new FileOrganizer.PreviewRequest(
                request.sourceRoot(),
                request.extensions(),
                request.instruction()
        ));
    }

    @PostMapping("/organize/confirm")
    public FileOrganizer.ConfirmResult confirmOrganizeFiles(@RequestBody FileOrganizeConfirmRequest request) {
        if (request.previewId() == null || request.previewId().isBlank()) {
            throw AppException.badRequest("previewId 不能为空");
        }
        return fileService.confirm(request.previewId().trim());
    }
}
