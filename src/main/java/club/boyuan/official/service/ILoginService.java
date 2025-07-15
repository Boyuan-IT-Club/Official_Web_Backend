package club.boyuan.official.service;

import club.boyuan.official.dto.LoginDTO;
import club.boyuan.official.dto.ResponseMessage;

public interface ILoginService {
    ResponseMessage login(LoginDTO loginDTO);
    ResponseMessage<?> loginByEmailPassword(String email, String password);
    ResponseMessage<?> loginByEmailCode(String email, String code);
    ResponseMessage<?> sendEmailVerificationCode(String email);
    ResponseMessage<?> sendSmsVerificationCode(String phone);
    ResponseMessage<?> loginByPhonePassword(String phone, String password);
    ResponseMessage<?> loginByPhoneCode(String phone, String code);
    ResponseMessage<?> loginByUsernamePassword(String username, String password);
    ResponseMessage<?> logout(String token);
    ResponseMessage<?> refreshToken(String refreshToken);
}