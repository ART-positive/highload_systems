package ru.itmo.courses.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.courses.common.api.Pagination;
import ru.itmo.courses.common.exception.ConflictException;
import ru.itmo.courses.common.exception.NotFoundException;
import ru.itmo.courses.course.repository.CourseRepository;
import ru.itmo.courses.enrollment.repository.EnrollmentRepository;
import ru.itmo.courses.user.dto.UserRequest;
import ru.itmo.courses.user.dto.UserResponse;
import ru.itmo.courses.user.model.AppUser;
import ru.itmo.courses.user.repository.UserRepository;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;

    public Page<UserResponse> list(int page, int size) {
        return users.findAll(Pagination.page(page, size)).map(this::toResponse);
    }

    public UserResponse get(long id) {
        return toResponse(users.findById(id).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        return toResponse(users.save(new AppUser(request.fullName().strip(), normalizeEmail(request.email()),
                request.role(), request.active())));
    }

    @Transactional
    public UserResponse update(long id, UserRequest request) {
        AppUser user = users.findLockedById(id).orElseThrow(() -> missing(id));
        if (user.getRole() != request.role() && hasHistory(id)) {
            throw new ConflictException("Нельзя менять роль пользователя, связанного с курсами или записями");
        }
        user.setFullName(request.fullName().strip());
        user.setEmail(normalizeEmail(request.email()));
        user.setRole(request.role());
        user.setActive(request.active());
        return toResponse(user);
    }

    @Transactional
    public void delete(long id) {
        AppUser user = users.findLockedById(id).orElseThrow(() -> missing(id));
        if (hasHistory(id)) {
            throw new ConflictException("У пользователя есть история. Деактивируйте его через active=false");
        }
        users.delete(user);
    }

    private boolean hasHistory(long id) {
        return courses.existsByProfessorId(id) || enrollments.existsByStudentId(id);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private NotFoundException missing(long id) {
        return new NotFoundException("Пользователь " + id + " не найден");
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive());
    }
}
