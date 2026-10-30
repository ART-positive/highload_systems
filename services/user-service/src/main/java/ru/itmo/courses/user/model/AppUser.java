package ru.itmo.courses.user.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {
    @Id
    private Long id;

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotNull
    private UserRole role;
    private boolean active;
    private boolean referenced;

    public AppUser(String fullName, String email, UserRole role, boolean active) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.active = active;
    }
}
