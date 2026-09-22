package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.CashEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CashEventRepository extends JpaRepository<CashEvent, String> {

    List<CashEvent> findByClientIdAndStateAndEffectiveAtLessThanEqual(
        String clientId, String state, Instant cutAt);

    List<CashEvent> findByClientId(String clientId);

    List<CashEvent> findBySourceFileId(String sourceFileId);

    @Query("SELECT SUM(e.amountPaise) FROM CashEvent e " +
           "WHERE e.clientId = :clientId AND e.state = 'POSTED' AND e.effectiveAt <= :cutAt")
    Long sumPostedBalanceAtCut(@Param("clientId") String clientId, @Param("cutAt") Instant cutAt);

    @Query("SELECT DISTINCT e.clientId FROM CashEvent e")
    List<String> findDistinctClientIds();
}
