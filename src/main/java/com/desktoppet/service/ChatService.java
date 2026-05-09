package com.desktoppet.service;

import com.desktoppet.controller.dto.ApiModels.ChatResponse;

public interface ChatService {
    ChatResponse reply(String message);
}
