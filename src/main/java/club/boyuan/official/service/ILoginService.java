package club.boyuan.official.service;

import club.boyuan.official.dto.LoginDTO;
import club.boyuan.official.dto.ResponseMessage;

public interface ILoginService {
    ResponseMessage login(LoginDTO loginDTO);
    ResponseMessage logout(String token);
    ResponseMessage refreshToken(String refreshToken);
}