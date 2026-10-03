package ru.itmo.courses.course.model;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import ru.itmo.courses.user.model.AppUser;
import ru.itmo.courses.program.model.StudyProgram;
import ru.itmo.courses.enrollment.model.Enrollment;

@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    @Setter
    private AppUser professor;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    @Setter
    private String title;

    @NotNull
    @Size(max = 4000)
    @Column(nullable = false, length = 4000)
    @Setter
    private String description;

    @Positive
    @Column(nullable = false)
    @Setter
    private int capacity;

    @NotNull
    @Column(nullable = false)
    @Setter
    private LocalDate startDate;

    @NotNull
    @Column(nullable = false)
    @Setter
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private CourseStatus status;

    @ManyToMany
    @JoinTable(name = "course_program",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "program_id"))
    private Set<StudyProgram> programs = new HashSet<>();

    @OneToMany(mappedBy = "course")
    @Getter(AccessLevel.NONE)
    private Set<Enrollment> enrollments = new HashSet<>();

    public Course(AppUser professor, String title, String description, int capacity, LocalDate startDate,
            LocalDate endDate, CourseStatus status) {
        this.professor = professor;
        this.title = title;
        this.description = description;
        this.capacity = capacity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    @AssertTrue(message = "Дата окончания не может быть раньше даты начала")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
