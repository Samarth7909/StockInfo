package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.CaseEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseEvidenceRepository extends JpaRepository<CaseEvidence, String> {
    @EntityGraph(attributePaths = {"sourceFile", "rawRow"})
    List<CaseEvidence> findByExceptionCaseId(String caseId);
}
