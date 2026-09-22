package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.NormalizedHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NormalizedHoldingRepository extends JpaRepository<NormalizedHolding, String> {

    List<NormalizedHolding> findByClientIdAndIsinAndPositionTypeAndCutAtAndDataSource(
        String clientId, String isin, String positionType, Instant cutAt, String dataSource);

    List<NormalizedHolding> findBySourceFileId(String sourceFileId);

    @Query("SELECT h FROM NormalizedHolding h WHERE h.cutAt = :cutAt AND h.dataSource = :dataSource")
    List<NormalizedHolding> findByCutAtAndDataSource(
        @Param("cutAt") Instant cutAt, @Param("dataSource") String dataSource);

    @Query("SELECT DISTINCT h.cutAt FROM NormalizedHolding h WHERE h.dataSource = :dataSource ORDER BY h.cutAt DESC")
    List<Instant> findDistinctCutAtsByDataSource(@Param("dataSource") String dataSource);
}
