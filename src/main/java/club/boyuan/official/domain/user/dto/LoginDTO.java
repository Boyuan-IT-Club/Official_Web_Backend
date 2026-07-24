package club.boyuan.official.domain.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDTO {
    private String username;
    private String password;
    private String authType;
    private String email;
    private String code;
    private String phone;

}