package com.desktoppet.controller;

import com.desktoppet.controller.dto.ApiModels.StartupResponse;
import com.desktoppet.service.StartupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/startup")
public class StartupController {
    private final StartupService startupService;

    public StartupController(StartupService startupService) {
        this.startupService = startupService;
    }

    @GetMapping
    public StartupResponse startup() {
        return startupService.startup();
    }
}
