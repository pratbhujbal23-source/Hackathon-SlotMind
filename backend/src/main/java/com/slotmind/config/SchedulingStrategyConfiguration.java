package com.slotmind.config;

import com.slotmind.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
public class SchedulingStrategyConfiguration {
    @Bean
    @Primary
    @ConditionalOnProperty(name = "scheduling.strategy", havingValue = "ai")
    SchedulingStrategy aiStrategy(AiSchedulingStrategy strategy) {
        return strategy;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "scheduling.strategy", havingValue = "rule", matchIfMissing = true)
    SchedulingStrategy ruleStrategy(RuleBasedSchedulingStrategy strategy) {
        return strategy;
    }
}
