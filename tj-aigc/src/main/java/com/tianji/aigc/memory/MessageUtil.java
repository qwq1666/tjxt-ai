package com.tianji.aigc.memory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.chat.messages.*;

public class MessageUtil {
    public static String toJson(Message message){
        var myMessage = BeanUtil.toBean(message, MyMessage.class);
        myMessage.setTextContent(message.getText());
        if (message instanceof AssistantMessage assistantMessage){
            myMessage.setToolCalls(assistantMessage.getToolCalls());
        }
        if (message instanceof ToolResponseMessage toolResponseMessage){
            myMessage.setToolResponses(toolResponseMessage.getResponses());
        }

        return JSONUtil.toJsonStr(myMessage);
    }

    public static Message toMessage(String json){
        var myMessage = JSONUtil.toBean(json,MyMessage.class);
        var messageType = MessageType.valueOf(myMessage.getMessageType());
        switch (messageType){
            case SYSTEM -> {
                return new SystemMessage(myMessage.getTextContent());
            }
            case USER ->{
                return UserMessage.builder()
                        .text(myMessage.getTextContent())
                        .metadata(myMessage.getMetadata())
                        .media(myMessage.getMedia())
                        .build();
            }
            case ASSISTANT -> {
                return new AssistantMessage(myMessage.getTextContent(),myMessage.getMetadata(),myMessage.getToolCalls());
            }
            case TOOL -> {
                return new ToolResponseMessage(myMessage.getToolResponses(),myMessage.getMetadata());
            }
        }
        throw new RuntimeException("Message data conversation failed");
    }
}
