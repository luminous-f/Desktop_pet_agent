package com.desktoppet.controller;

import com.desktoppet.controller.dto.ApiModels.ProfileRequest;
import com.desktoppet.controller.dto.ApiModels.ProfileResponse;
import com.desktoppet.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse profile() {
        return new ProfileResponse(profileService.currentProfile());
    }

    @PutMapping
    public ProfileResponse saveProfile(@RequestBody ProfileRequest request) {
        String profileText = request.profileText() == null ? "" : request.profileText();
        profileService.saveProfile(profileText);
        return new ProfileResponse(profileService.currentProfile());
    }
}
