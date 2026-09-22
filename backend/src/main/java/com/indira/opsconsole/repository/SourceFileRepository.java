package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.SourceFile;
import com.indira.opsconsole.domain.enums.SourceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceFileRepository extends JpaRepository<SourceFile, String> {

    @Override
    @EntityGraph(attributePaths = "importedBy")
    Optional<SourceFile> findById(String id);

    /** Used for idempotency — same stream + same hash means no-op. */
    Optional<SourceFile> findByStreamNameAndSha256Hash(String streamName, String sha256Hash);

    List<SourceFile> findByStreamNameOrderByReceivedAtDesc(String streamName);

    List<SourceFile> findByStatus(SourceStatus status);

    @Query("SELECT sf FROM SourceFile sf LEFT JOIN FETCH sf.importedBy ORDER BY sf.receivedAt DESC")
    List<SourceFile> findAllOrderByReceivedAtDesc();
}
