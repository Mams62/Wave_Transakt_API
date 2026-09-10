package com.wavetransakt.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name is too long")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name is too long")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 255, message = "Email is too long")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^\\+?[0-9]{10,15}$",
            message = "Invalid phone number"
    )
    private String phone;

    @NotBlank(message = "Account PIN is required")
    @Pattern(
            regexp = "^\\d{6}$",
            message = "Account PIN must be exactly 6 digits"
    )
    private String accountPin;

    @NotBlank(message = "BVN is required")
    @Pattern(regexp = "^\\d{11}$", message = "BVN must be 11 digits")
    private String bvn;

    @NotBlank(message = "NIN is required")
    @Pattern(regexp = "^\\d{11}$", message = "NIN must be 11 digits")
    private String nin;

    @NotBlank(message = "State is required")
    @Size(max = 80, message = "State is too long")
    private String state;

    @NotBlank(message = "Local government is required")
    @Size(max = 100, message = "Local government is too long")
    private String localGovernment;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Gender is required")
    @Size(max = 30, message = "Gender value is too long")
    private String gender;

    @NotBlank(message = "Transaction PIN is required")
    @Pattern(
            regexp = "^\\d{6}$",
            message = "Transaction PIN must be exactly 6 digits"
    )
    private String transactionPin;
}
