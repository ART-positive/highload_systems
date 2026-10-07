package ru.itmo.courses.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.itmo.courses.user.dto.UserResponse;
import ru.itmo.courses.user.model.UserRole;

@FeignClient(name = "user-service", configuration = UserClientConfiguration.class,
        fallbackFactory = UserDirectoryFallback.class)
public interface UserDirectory {
    @PutMapping("/internal/users/{id}/reference")
    UserResponse requireActive(@PathVariable("id") long id, @RequestParam("role") UserRole role);
}
