package ru.itmo.courses.program.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.itmo.courses.course.model.Course;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "study_program")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyProgram {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Pattern(regexp = "[A-Z0-9_-]+")
    @Size(max = 40)
    @Column(nullable = false, unique = true, length = 40)
    @Setter
    private String code;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    @Setter
    private String name;

    @NotNull
    @Size(max = 4000)
    @Column(nullable = false, length = 4000)
    @Setter
    private String description;

    @Column(nullable = false)
    @Setter
    private boolean archived;

    @ManyToMany(mappedBy = "programs")
    @Getter(AccessLevel.NONE)
    private Set<Course> courses = new HashSet<>();

    public StudyProgram(String code, String name, String description, boolean archived) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.archived = archived;
    }

}
