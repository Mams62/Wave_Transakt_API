package com.wavetransakt.user.dto;

import com.wavetransakt.user.entity.User;

import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String state,
        String localGovernment,
        LocalDate dateOfBirth,
        String gender,
        String walletNumber,
        boolean bvnVerified,
        boolean ninVerified
) {
    public static UserProfileResponse from(
            User user,
            String walletNumber
    ) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getState(),
                user.getLocalGovernment(),
                user.getDateOfBirth(),
                user.getGender(),
                walletNumber,
                Boolean.TRUE.equals(user.getBvnVerified()),
                Boolean.TRUE.equals(user.getNinVerified())
        );
    }
}
