package club.boyuan.official.dto;

public class LoginDTO {
    private String username;
    private String password;
    private String authType;
    private String email;
    private String code;
    private String phone;

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

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public String getPhone() {
        return phone;
    }

}