package com.tianji.aigc.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Getter
@Configuration
@RequiredArgsConstructor
public class SystemPromptConfig {
    private final NacosConfigManager nacosConfigManager;
    private final AIProperties aiProperties;
    private final AtomicReference<String> chatSystemMessage = new AtomicReference<>();

    @PostConstruct
    public void init(){
        loadConfig(aiProperties.getSystem().getChat(),chatSystemMessage);
    }

    private void loadConfig(AIProperties.System.Chat chatConfig,AtomicReference<String> target){
        try{
            var dataId = chatConfig.getDataId();
            var group = chatConfig.getGroup();
            var timeoutMs = chatConfig.getTimeoutMs();
            var config = nacosConfigManager.getConfigService().getConfig(dataId,group,timeoutMs);
            target.set(config);
            log.info("读取{}成功，内容为：{}",target,config);
            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String info) {
                    target.set(info);
                    log.info("更新{}成功，内容为：{}",target,info);
                }
            });
        } catch (Exception e){
            log.error("加载配置失败",e);
        }
    }
}
