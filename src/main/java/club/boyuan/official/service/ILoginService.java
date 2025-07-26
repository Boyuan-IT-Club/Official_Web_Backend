package club.boyuan.official.service;

import club.boyuan.official.dto.LoginDTO;
import club.boyuan.official.dto.ResponseMessage;

public interface ILoginService {
    ResponseMessage<?> loginByEmailPassword(String email, String password);
    ResponseMessage<?> loginByEmailCode(String email, String code);
    ResponseMessage<?> loginByPhonePassword(String phone, String password);
    ResponseMessage<?> loginByPhoneCode(String phone, String code);
    ResponseMessage<?> loginByUsernamePassword(String username, String password);

    /**
     * 生成验证码
     * @param identifier 标识符（手机号或邮箱）
     * @return 生成的验证码
     */
    String generateVerificationCode(String identifier);

    /**
     * 保存验证码到Redis
     * @param identifier 标识符（手机号或邮箱）
     * @param code 验证码
     * @param expireSeconds 过期时间（秒）
     */
    void saveVerificationCode(String identifier, String code, long expireSeconds);
}