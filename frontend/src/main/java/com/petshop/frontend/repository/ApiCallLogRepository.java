package com.petshop.frontend.repository;

import com.petshop.frontend.model.ApiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {
    List<ApiCallLog> findByContributorIdOrderByCalledAtDesc(Long contributorId);

    List<ApiCallLog> findTop20ByOrderByCalledAtDesc();

    @Query(value = "SELECT * FROM api_call_logs ORDER BY called_at DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<ApiCallLog> findPaginated(@Param("offset") int offset, @Param("limit") int limit);

    @Query(value = "SELECT * FROM api_call_logs WHERE contributor_id = :contributorId ORDER BY called_at DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    List<ApiCallLog> findByContributorIdPaginated(@Param("contributorId") Long contributorId, @Param("offset") int offset, @Param("limit") int limit);

    @Query("SELECT COUNT(a) FROM ApiCallLog a")
    Long countAll();

    @Query("SELECT COUNT(a) FROM ApiCallLog a WHERE a.contributorId = :contributorId")
    Long countByContributorId(@Param("contributorId") Long contributorId);

    @Query("SELECT COUNT(a) FROM ApiCallLog a WHERE a.status = :status")
    Long countByStatus(@Param("status") String status);

    @Query("SELECT a.contributorId, COUNT(a) FROM ApiCallLog a GROUP BY a.contributorId")
    List<Object[]> countByContributor();

    @Query("SELECT a.contributorId, COUNT(a) FROM ApiCallLog a WHERE a.status = :status GROUP BY a.contributorId")
    List<Object[]> countByContributorAndStatus(@Param("status") String status);

    @Query("SELECT DISTINCT a.endpoint FROM ApiCallLog a WHERE a.contributorId = :contributorId")
    List<String> findDistinctEndpointsByContributorId(@Param("contributorId") Long contributorId);

    @Query("SELECT MIN(a.calledAt) FROM ApiCallLog a WHERE a.contributorId = :contributorId")
    LocalDateTime findFirstCallByContributorId(@Param("contributorId") Long contributorId);

    @Query("SELECT MAX(a.calledAt) FROM ApiCallLog a WHERE a.contributorId = :contributorId")
    LocalDateTime findLastCallByContributorId(@Param("contributorId") Long contributorId);

    @Query("SELECT a.status, COUNT(a) FROM ApiCallLog a GROUP BY a.status")
    List<Object[]> countGroupByStatus();

    @Query(value = "SELECT DATE(called_at) as call_date, COUNT(*) as total FROM api_call_logs GROUP BY DATE(called_at) ORDER BY call_date", nativeQuery = true)
    List<Object[]> countByDate();

    @Query("SELECT a.httpMethod, COUNT(a) FROM ApiCallLog a GROUP BY a.httpMethod")
    List<Object[]> countByHttpMethod();

    @Query("SELECT a.endpoint, COUNT(a) FROM ApiCallLog a GROUP BY a.endpoint ORDER BY COUNT(a) DESC")
    List<Object[]> topEndpoints();

    @Query("SELECT a.httpMethod, a.status, COUNT(a) FROM ApiCallLog a GROUP BY a.httpMethod, a.status")
    List<Object[]> countByMethodAndStatus();
}