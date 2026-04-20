package com.petshop.frontend.service;

import com.petshop.frontend.model.Contributor;
import com.petshop.frontend.repository.ContributorRepository;
import org.springframework.stereotype.Service;
import com.petshop.frontend.repository.ApiCallLogRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final ApiCallLogRepository apiCallLogRepository;
    private final ContributorRepository contributorRepository;
    private final ContributorService contributorService;

    public DashboardService(ApiCallLogRepository apiCallLogRepository, 
                             ContributorRepository contributorRepository,
                             ContributorService contributorService) {
        this.apiCallLogRepository = apiCallLogRepository;
        this.contributorRepository = contributorRepository;
        this.contributorService = contributorService;
    }

    public long getTotalApiCalls() {
        Long count = apiCallLogRepository.countAll();
        return count != null ? count : 0;
    }

    public long getSuccessfulCalls() {
        Long count = apiCallLogRepository.countByStatus("SUCCESS");
        return count != null ? count : 0;
    }

    public long getFailedCalls() {
        Long count = apiCallLogRepository.countByStatus("FAILED");
        return count != null ? count : 0;
    }

    public long getProcessingCalls() {
        Long count = apiCallLogRepository.countByStatus("PROCESSING");
        return count != null ? count : 0;
    }

    public List<ContributorStats> getContributorStats() {
        List<Object[]> totalCounts = apiCallLogRepository.countByContributor();
        List<Object[]> successCounts = apiCallLogRepository.countByContributorAndStatus("SUCCESS");
        List<Object[]> failedCounts = apiCallLogRepository.countByContributorAndStatus("FAILED");

        return totalCounts.stream().map(row -> {
            Long contributorId = (Long) row[0];
            Long total = (Long) row[1];

            Contributor contributor = contributorRepository.findById(contributorId).orElse(null);
            if (contributor == null) return null;

            long success = findCountByContributorId(successCounts, contributorId);
            long failed = findCountByContributorId(failedCounts, contributorId);
            long successRate = total > 0 ? (success * 100 / total) : 0;

            int totalEndpoints = contributorService.getTotalEndpointsForContributor(contributor.getUsername());
            List<String> calledEndpoints = apiCallLogRepository.findDistinctEndpointsByContributorId(contributorId);
            int testedEndpoints = calledEndpoints.size();

            LocalDateTime firstCall = apiCallLogRepository.findFirstCallByContributorId(contributorId);
            LocalDateTime lastCall = apiCallLogRepository.findLastCallByContributorId(contributorId);

            return new ContributorStats(
                contributor,
                total,
                success,
                failed,
                successRate,
                testedEndpoints,
                totalEndpoints,
                firstCall,
                lastCall
            );
        }).filter(stats -> stats != null).toList();
    }

    public Map<String, Long> getStatusDistribution() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("Success", 0L);
        distribution.put("Failed", 0L);
        distribution.put("Processing", 0L);
        
        List<Object[]> results = apiCallLogRepository.countGroupByStatus();
        for (Object[] row : results) {
            String status = (String) row[0];
            Long count = (Long) row[1];
            if ("SUCCESS".equals(status)) {
                distribution.put("Success", count);
            } else if ("FAILED".equals(status)) {
                distribution.put("Failed", count);
            } else {
                distribution.put("Processing", count);
            }
        }
        return distribution;
    }

    public Map<String, Long> getDailyApiCalls() {
        Map<String, Long> dailyCalls = new LinkedHashMap<>();
        List<Object[]> results = apiCallLogRepository.countByDate();
        for (Object[] row : results) {
            if (row[0] != null) {
                String date = row[0].toString();
                Long count = ((Number) row[1]).longValue();
                dailyCalls.put(date, count);
            }
        }
        return dailyCalls;
    }

    public Map<String, Long> getHttpMethodDistribution() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        List<Object[]> results = apiCallLogRepository.countByHttpMethod();
        for (Object[] row : results) {
            distribution.put((String) row[0], (Long) row[1]);
        }
        return distribution;
    }

    public List<Map<String, Object>> getTopEndpoints() {
        List<Map<String, Object>> topList = new ArrayList<>();
        List<Object[]> results = apiCallLogRepository.topEndpoints();
        for (Object[] row : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("endpoint", row[0]);
            entry.put("count", ((Number) row[1]).longValue());
            topList.add(entry);
        }
        return topList.stream().limit(5).toList();
    }

public Map<String, Map<String, Long>> getMethodStatusMatrix() {
        Map<String, Map<String, Long>> matrix = new LinkedHashMap<>();
        List<Object[]> results = apiCallLogRepository.countByMethodAndStatus();
        for (Object[] row : results) {
            String method = (String) row[0];
            String status = (String) row[1];
            Long count = (Long) row[2];
            matrix.computeIfAbsent(method, k -> new LinkedHashMap<>()).put(status, count);
        }
        return matrix;
    }

    public Map<String, Long> getContributorApiCalls() {
        Map<String, Long> contributorCalls = new LinkedHashMap<>();
        List<Contributor> contributors = contributorRepository.findAll();
        for (Contributor c : contributors) {
            Long count = apiCallLogRepository.countByContributorId(c.getId());
            contributorCalls.put(c.getName(), count != null ? count : 0L);
        }
        return contributorCalls;
    }

    private long findCountByContributorId(List<Object[]> counts, Long contributorId) {
        for (Object[] row : counts) {
            if (row[0].equals(contributorId)) {
                return (Long) row[1];
            }
        }
        return 0;
    }

    public record ContributorStats(
        Contributor contributor,
        long totalCalls,
        long successCalls,
        long failedCalls,
        long successRate,
        int testedEndpoints,
        int totalEndpoints,
        LocalDateTime firstCall,
        LocalDateTime lastCall
    ) {}
}


