package com.spring.transfer.service;

import com.spring.transfer.repository.SpringArchiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ElasticSpecCacheService {
    private static final String ELASTIC_SPECS_KEY = "spring:elastic:specs";
    private final RedisTemplate<String, Object> redisTemplate;
    private final SpringArchiveRepository springArchiveRepository;

    public void warmUpCache() {
        List<BigDecimal> specs = springArchiveRepository.findDistinctElasticCoefficients();
        redisTemplate.delete(ELASTIC_SPECS_KEY);
        specs.forEach(this::addElasticSpec);
    }

    public void addElasticSpec(BigDecimal value) {
        if (value != null) {
            redisTemplate.opsForZSet().add(ELASTIC_SPECS_KEY, value.toPlainString(), value.doubleValue());
        }
    }

    public List<Double> getElasticSpecs(Double min, Double max) {
        double minScore = min != null ? min : Double.NEGATIVE_INFINITY;
        double maxScore = max != null ? max : Double.POSITIVE_INFINITY;
        Set<Object> values = redisTemplate.opsForZSet().rangeByScore(ELASTIC_SPECS_KEY, minScore, maxScore);
        return values.stream()
                .map(v -> Double.parseDouble(v.toString()))
                .collect(Collectors.toList());
    }

    public List<Double> getAllElasticSpecs() {
        return getElasticSpecs(null, null);
    }
}
