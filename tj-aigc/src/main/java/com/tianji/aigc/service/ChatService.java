package com.tianji.aigc.service;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import reactor.core.publisher.Flux;

public interface ChatService {

    Flux<ChatEventVO> chat(String question,String sessionId);

    void stop(String sessionId);

    static String getConversationId(String sessionId){
        return UserContext.getUser() + "-" + sessionId;
    }
}
