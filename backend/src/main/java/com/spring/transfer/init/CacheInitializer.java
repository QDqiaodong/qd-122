package com.spring.transfer.init;

import com.spring.transfer.service.ElasticSpecCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInitializer implements CommandLineRunner {
    private final ElasticSpecCacheService elasticSpecCacheService;

    @Override
    public void run(String... args) {
        log.info("开始预热Redis缓存...");
        elasticSpecCacheService.warmUpCache();
        log.info("Redis缓存预热完成");
    }
}
