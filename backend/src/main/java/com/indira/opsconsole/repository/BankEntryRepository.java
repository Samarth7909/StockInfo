package com.indira.opsconsole.repository;

import com.indira.opsconsole.domain.entity.BankEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankEntryRepository extends JpaRepository<BankEntry, String> {
    List<BankEntry> findByUtrReference(String utrReference);
    List<BankEntry> findByClientId(String clientId);
    List<BankEntry> findBySourceFileId(String sourceFileId);

    /** Count how many rows share this UTR — duplicates are CONFLICTING_EVIDENCE. */
    long countByUtrReference(String utrReference);
}
