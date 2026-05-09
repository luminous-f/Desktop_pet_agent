package com.desktoppet.controller;

import com.desktoppet.common.AppException;
import com.desktoppet.controller.dto.ApiModels.ChatRequest;
import com.desktoppet.controller.dto.ApiModels.ChatResponse;
import com.desktoppet.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw AppException.badRequest("message 不能为空");
        }
        return chatService.reply(request.message().trim());
    }
}
