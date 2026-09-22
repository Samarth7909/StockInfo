package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.DpPendingMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DpPendingMovementRepository extends JpaRepository<DpPendingMovement, String> {

    List<DpPendingMovement> findByClientIdAndIsinAndCutAt(String clientId, String isin, Instant cutAt);

    List<DpPendingMovement> findBySourceFileId(String sourceFileId);

    /**
     * All pending movements for a given cut timestamp.
     * Used by case detection rule 2 (PENDING_DP) to avoid a full table scan.
     */
    @Query("SELECT pm FROM DpPendingMovement pm WHERE pm.cutAt = :cutAt")
    List<DpPendingMovement> findByCutAt(@Param("cutAt") Instant cutAt);
}
