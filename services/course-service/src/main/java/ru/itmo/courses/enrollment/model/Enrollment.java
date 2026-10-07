package ru.itmo.courses.enrollment.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import ru.itmo.courses.course.model.Course;

@Entity
@Table(name = "enrollment", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Positive
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter
    private EnrollmentStatus status;

    @Min(0)
    @Max(100)
    @Setter
    private Integer grade;

    @NotNull
    @Column(nullable = false)
    private Instant enrolledAt;

    public Enrollment(Long studentId, Course course, EnrollmentStatus status, Integer grade, Instant enrolledAt) {
        this.studentId = studentId;
        this.course = course;
        this.status = status;
        this.grade = grade;
        this.enrolledAt = enrolledAt;
    }

    public void reactivate(Instant enrolledAt) {
        this.status = EnrollmentStatus.ENROLLED;
        this.grade = null;
        this.enrolledAt = enrolledAt;
    }
}
