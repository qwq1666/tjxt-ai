package com.tianji.aigc.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constant.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private static final Map<String ,Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();
    private final ChatMemory chatMemory;
    private static final ChatEventVO STOP_EVENT = ChatEventVO.builder().eventType(ChatEventTypeEnum.STOP.getValue()).build();
    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        var conversationId = ChatService.getConversationId(sessionId);
        var outputBuilder = new StringBuilder();
        var requestId = IdUtil.fastSimpleUUID();
        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get())
                        .param("now", DateUtil.now()))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID,conversationId))
                .toolContext(Map.of(Constant.REQUEST_ID,requestId))//通过工具上下文传递参数
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(()->GENERATE_STATUS.put(sessionId,true))
                .doOnError(throwable -> GENERATE_STATUS.remove(sessionId))
                .doOnCancel(()->{
                    this.saveStopHistoryRecord(conversationId,outputBuilder.toString());
                })
                .doOnComplete(()-> GENERATE_STATUS.remove(sessionId))
                .takeWhile(response -> {
                    return GENERATE_STATUS.getOrDefault(sessionId,false);
                })
                .map(chatResponse -> {
                    String text = chatResponse.getResult().getOutput().getText();
                    outputBuilder.append(text);
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.defer(()->{
                    var map = ToolResultHolder.get(requestId);
                    if (CollUtil.isNotEmpty(map)) {
                        ToolResultHolder.remove(requestId);
                        var chatEventVO = ChatEventVO.builder()
                                .eventData(map)
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .build();
                        return Flux.just(chatEventVO, STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT);
                }));
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }

    private void saveStopHistoryRecord(String conversationId,String content){
        this.chatMemory.add(conversationId,new AssistantMessage(content));
    }
}
