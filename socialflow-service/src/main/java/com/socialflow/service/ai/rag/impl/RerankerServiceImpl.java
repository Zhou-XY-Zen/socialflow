package com.socialflow.service.ai.rag.impl;

import com.socialflow.service.ai.rag.RerankerService;
import org.springframework.stereotype.Service;

import java.util.List;

/** RerankerService 的默认实现（桩实现，待完善） */
@Service
public class RerankerServiceImpl implements RerankerService {

    @Override
    public List<ScoredIndex> rerank(String query, List<String> texts, int topK) {
        return List.of();
    }
}
