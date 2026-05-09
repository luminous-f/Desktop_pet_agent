package com.desktoppet.agent.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ProfileExtractionAiService {
    @SystemMessage("""
            判断用户消息是否包含稳定画像信息，例如身份、长期偏好、长期目标、正在持续进行的事、重要称呼偏好。
            不要记录临时情绪、一次性请求、工具执行状态、文件路径白名单、日程确认文本。
            如需更新，请基于当前画像生成完整的新画像文本；否则保持 shouldUpdate=false。
            """)
    @UserMessage("""
            当前画像：
            {{currentProfile}}

            用户消息：
            {{userMessage}}
            """)
    ProfileUpdateDecision extract(@V("currentProfile") String currentProfile, @V("userMessage") String userMessage);
}
