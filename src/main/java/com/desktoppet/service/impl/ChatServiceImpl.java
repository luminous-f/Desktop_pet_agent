package com.desktoppet.service.impl;

import com.desktoppet.agent.DesktopPetAgent;
import com.desktoppet.controller.dto.ApiModels.ChatResponse;
import com.desktoppet.service.ChatService;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {
    private final DesktopPetAgent agent;

    public ChatServiceImpl(DesktopPetAgent agent) {
        this.agent = agent;
    }

    @Override
    public ChatResponse reply(String message) {
        return new ChatResponse(agent.reply(message), "speaking");
    }
}
