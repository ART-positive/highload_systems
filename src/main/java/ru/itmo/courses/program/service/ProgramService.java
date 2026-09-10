package ru.itmo.courses.program.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.courses.common.api.Pagination;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.NotFoundException;
import ru.itmo.courses.course.repository.CourseRepository;
import ru.itmo.courses.program.dto.ProgramRequest;
import ru.itmo.courses.program.dto.ProgramResponse;
import ru.itmo.courses.program.model.StudyProgram;
import ru.itmo.courses.program.repository.ProgramRepository;

@Service
@Transactional(readOnly = true)
public class ProgramService {
    private final ProgramRepository programs;
    private final CourseRepository courses;

    public ProgramService(ProgramRepository programs, CourseRepository courses) {
        this.programs = programs;
        this.courses = courses;
    }

    public Page<ProgramResponse> list(int page, int size) {
        return programs.findAll(Pagination.page(page, size)).map(ProgramService::toResponse);
    }

    public Page<ProgramResponse> listForCourse(long courseId, int page, int size) {
        if (!courses.existsById(courseId)) {
            throw new NotFoundException("Курс " + courseId + " не найден");
        }
        return programs.findByCourseId(courseId, Pagination.page(page, size)).map(ProgramService::toResponse);
    }

    public ProgramResponse get(long id) {
        return toResponse(programs.findById(id).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public ProgramResponse create(ProgramRequest request) {
        return toResponse(programs.save(new StudyProgram(request.code(), request.name().strip(),
                request.description(), request.archived())));
    }

    @Transactional
    public ProgramResponse update(long id, ProgramRequest request) {
        StudyProgram program = programs.findLockedById(id).orElseThrow(() -> missing(id));
        program.setCode(request.code());
        program.setName(request.name().strip());
        program.setDescription(request.description());
        program.setArchived(request.archived());
        return toResponse(program);
    }

    @Transactional
    public void delete(long id) {
        StudyProgram program = programs.findLockedById(id).orElseThrow(() -> missing(id));
        if (courses.existsByProgramsId(id)) {
            throw new ConflictException("Программа содержит курсы. Архивируйте её через archived=true");
        }
        programs.delete(program);
    }

    private NotFoundException missing(long id) {
        return new NotFoundException("Образовательная программа " + id + " не найдена");
    }

    private static ProgramResponse toResponse(StudyProgram program) {
        return new ProgramResponse(program.getId(), program.getCode(), program.getName(),
                program.getDescription(), program.getArchived());
    }
}
