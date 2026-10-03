package ru.itmo.courses.enrollment.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.itmo.courses.enrollment.model.Enrollment;
import ru.itmo.courses.enrollment.model.EnrollmentStatus;

import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    Optional<Enrollment> findByStudentIdAndCourseId(long studentId, long courseId);

    @Query("select e.course.id from Enrollment e where e.id = :id")
    Optional<Long> findCourseIdById(long id);

    long countByCourseIdAndStatus(long courseId, EnrollmentStatus status);

    boolean existsByStudentId(long studentId);

    boolean existsByCourseIdAndStatusAndGradeIsNull(long courseId, EnrollmentStatus status);

    @Query("""
            select e from Enrollment e
            where (:studentId is null or e.student.id = :studentId)
              and (:courseId is null or e.course.id = :courseId)
              and (:status is null or e.status = :status)
            """)
    Page<Enrollment> search(Long studentId, Long courseId, EnrollmentStatus status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("update Enrollment e set e.status = :target where e.course.id = :courseId and e.status = :source")
    int changeStatus(long courseId, EnrollmentStatus source, EnrollmentStatus target);
}
