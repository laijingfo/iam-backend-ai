package com.lenovo.ai.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public AgentRegistry(List<Agent> all) {
        for (Agent a : all) {
            Agent prev = agents.put(a.name(), a);
            if (prev != null) log.warn("Agent 名冲突: {}", a.name());
        }
        log.info(">>> Agent 注册完成，共 {} 个: {}", agents.size(), agents.keySet());
    }

    public Agent get(String name) { return name == null ? null : agents.get(name); }

    public List<Agent> byIntent(String intent) {
        if (intent == null) return List.of();
        return agents.values().stream()
                .filter(a -> a.intents().contains(intent))
                .toList();
    }

    public Collection<Agent> all() { return agents.values(); }

    public boolean contains(String name) { return name != null && agents.containsKey(name); }
}