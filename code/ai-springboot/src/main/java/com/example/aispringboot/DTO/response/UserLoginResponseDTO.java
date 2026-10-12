package com.example.aispringboot.DTO.response;

import lombok.Data;

@Data
public class UserLoginResponseDTO {
    private String token;
    private String roleType;
    private UserDetailResponseDTO userInfo;

    @Data
    public static class UserDetailResponseDTO {
        private Long id;
        private String username;
        private String email;
        private String phone;
        private String nickname;
        private String avatar;
        private Integer gender;
        private String genderDisplayName;
        private String birthday;
        private Integer userType;
        private String userTypeDisplayName;
        private Integer status;
        private String statusDisplayName;
        private String displayName;
        private String createdAt;
        private String updatedAt;
    }

}
