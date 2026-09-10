package ru.itmo.courses.enrollment.model;

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
import java.time.Instant;
import ru.itmo.courses.user.model.AppUser;
import ru.itmo.courses.course.model.Course;

@Entity
@Table(name = "enrollment", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_id"}))
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Min(0)
    @Max(100)
    private Integer grade;

    @NotNull
    @Column(nullable = false)
    private Instant enrolledAt;

    protected Enrollment() {
    }

    public Enrollment(AppUser student, Course course, EnrollmentStatus status, Integer grade, Instant enrolledAt) {
        this.student = student;
        this.course = course;
        this.status = status;
        this.grade = grade;
        this.enrolledAt = enrolledAt;
    }

    public Long getId() {
        return id;
    }

    public AppUser getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatus status) {
        this.status = status;
    }

    public Integer getGrade() {
        return grade;
    }

    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void reactivate(Instant enrolledAt) {
        this.status = EnrollmentStatus.ENROLLED;
        this.grade = null;
        this.enrolledAt = enrolledAt;
    }
}
