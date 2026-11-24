package Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Ninova.entity.PoSequence;
import jakarta.persistence.LockModeType;

public interface PoSequenceRepository extends JpaRepository<PoSequence, Integer> {

    // Creates the row for the year if it does not exist yet
    @Modifying
    @Query(value = "INSERT IGNORE INTO po_sequences (`year`, `last_value`) VALUES (:year, 0)", nativeQuery = true)
    void ensureRow(@Param("year") int year);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PoSequence s where s.seqYear = :year")
    Optional<PoSequence> lockByYear(@Param("year") int year);
}