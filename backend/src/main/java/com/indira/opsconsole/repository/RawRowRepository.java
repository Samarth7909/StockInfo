package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.RawRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawRowRepository extends JpaRepository<RawRow, String> {
    List<RawRow> findBySourceFileIdOrderByRowIndex(String sourceFileId);
}
