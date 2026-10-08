package ru.itmo.courses.course.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.itmo.courses.course.model.Course;
import ru.itmo.courses.course.model.CourseStatus;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Course> findLockedById(long id);

    @Query("""
            select c from Course c
            where (:status is null or c.status = :status)
              and (:programId is null or exists
                (select p.id from Course other join other.programs p
                 where other.id = c.id and p.id = :programId))
            """)
    Page<Course> search(CourseStatus status, Long programId, Pageable pageable);

    @Query("""
            select c from Course c
            where c.id > :afterId
              and (:status is null or c.status = :status)
              and (:programId is null or exists
                (select p.id from Course other join other.programs p
                 where other.id = c.id and p.id = :programId))
            """)
    Slice<Course> scroll(long afterId, CourseStatus status, Long programId, Pageable pageable);

    boolean existsByProfessorId(long professorId);

    boolean existsByProgramsId(long programId);
}
