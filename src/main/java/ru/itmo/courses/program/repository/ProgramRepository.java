package ru.itmo.courses.program.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.itmo.courses.program.model.StudyProgram;

import java.util.Optional;

public interface ProgramRepository extends JpaRepository<StudyProgram, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudyProgram p where p.id = :id")
    Optional<StudyProgram> findLockedById(long id);

    @Query("select p from Course c join c.programs p where c.id = :courseId")
    Page<StudyProgram> findByCourseId(long courseId, Pageable pageable);
}
