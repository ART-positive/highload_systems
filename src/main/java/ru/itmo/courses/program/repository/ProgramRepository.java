package ru.itmo.courses.program.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.itmo.courses.program.model.StudyProgram;

import java.util.Optional;

public interface ProgramRepository extends JpaRepository<StudyProgram, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudyProgram> findLockedById(long id);

    Page<StudyProgram> findByCoursesId(long courseId, Pageable pageable);
}
