package com.resourceapp.backend.dto;

/**
 * Small plain request/response shapes for the auth endpoints, kept in one file since
 * none of them need to be JPA entities.
 */
public class AuthDtos {

    private AuthDtos() {
    }

    public static class AuthRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class AuthResponse {
        private String username;
        private String role;

        // Only populated when this login belongs to a resource person (role == USER):
        // lets the frontend greet them by name and jump straight to their own data
        // without a manual "which resource are you" picker. Null for the ADMIN account.
        private Long resourceId;
        private String resourceNameId;
        private String resourceName;

        public AuthResponse(String username, String role) {
            this.username = username;
            this.role = role;
        }

        public AuthResponse(String username, String role, Long resourceId, String resourceNameId, String resourceName) {
            this.username = username;
            this.role = role;
            this.resourceId = resourceId;
            this.resourceNameId = resourceNameId;
            this.resourceName = resourceName;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public Long getResourceId() {
            return resourceId;
        }

        public String getResourceNameId() {
            return resourceNameId;
        }

        public String getResourceName() {
            return resourceName;
        }
    }

    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
