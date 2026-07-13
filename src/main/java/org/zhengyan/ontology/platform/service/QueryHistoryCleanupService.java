package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.repository.QueryHistoryRepository;

import java.time.LocalDateTime;

@Service
public class QueryHistoryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryCleanupService.class);

    private final QueryHistoryRepository queryHistoryRepository;
    private final int retentionDays;

    public QueryHistoryCleanupService(QueryHistoryRepository queryHistoryRepository,
                                      @Value("${ontology.query-history.retention-days:30}") int retentionDays) {
        this.queryHistoryRepository = queryHistoryRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = queryHistoryRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} query history entries older than {} days", deleted, retentionDays);
        }
    }
}
