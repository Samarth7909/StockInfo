package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.ExceptionCase;
import com.indira.opsconsole.domain.enums.CaseSeverity;
import com.indira.opsconsole.domain.enums.CaseState;
import com.indira.opsconsole.domain.enums.CaseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExceptionCaseRepository extends JpaRepository<ExceptionCase, String> {

    Page<ExceptionCase> findByClientIdInAndSeverityAndState(
        List<String> clientIds, CaseSeverity severity, CaseState state, Pageable pageable);

    Page<ExceptionCase> findByClientIdInAndSeverity(
        List<String> clientIds, CaseSeverity severity, Pageable pageable);

    Page<ExceptionCase> findByClientIdInAndState(
        List<String> clientIds, CaseState state, Pageable pageable);

    Page<ExceptionCase> findByClientIdIn(List<String> clientIds, Pageable pageable);

    List<ExceptionCase> findByClientIdAndIsinAndCutAtAndCaseType(
        String clientId, String isin, Instant cutAt, CaseType caseType);

    List<ExceptionCase> findByClientIdAndCaseTypeAndStateNot(
        String clientId, CaseType caseType, CaseState state);

    /**
     * Count of non-RESOLVED cases visible to a set of clients.
     * Uses an enum parameter to avoid string-literal comparisons in JPQL.
     */
    @Query("SELECT COUNT(c) FROM ExceptionCase c " +
           "WHERE c.clientId IN :clientIds AND c.state <> :resolved")
    long countOpenByClientIds(
        @Param("clientIds") List<String> clientIds,
        @Param("resolved")  CaseState resolved);

    /**
     * Count of non-RESOLVED cases grouped by severity for a set of clients.
     */
    @Query("SELECT c.severity, COUNT(c) FROM ExceptionCase c " +
           "WHERE c.clientId IN :clientIds AND c.state <> :resolved " +
           "GROUP BY c.severity")
    List<Object[]> countBySeverityForClients(
        @Param("clientIds") List<String> clientIds,
        @Param("resolved")  CaseState resolved);

    Optional<ExceptionCase> findByIdAndClientIdIn(String id, List<String> clientIds);

    /** All distinct client IDs that have at least one case — used by OPS_LEAD / AUDITOR scope. */
    @Query("SELECT DISTINCT c.clientId FROM ExceptionCase c")
    List<String> findDistinctClientIds();
}
