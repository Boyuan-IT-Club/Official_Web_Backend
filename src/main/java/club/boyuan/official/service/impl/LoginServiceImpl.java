package club.boyuan.official.service.impl;

import club.boyuan.official.dto.LoginDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.ILoginService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.VerificationCodeService;
import club.boyuan.official.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Collections;

/**
 * 登录服务实现类
 * 负责处理多种登录方式的验证逻辑，包括邮箱密码、邮箱验证码、手机密码、手机验证码和用户名密码登录
 */
@Service
public class LoginServiceImpl implements ILoginService {

    /**
     * 用户服务，用于获取用户信息
     */
    @Autowired
    private IUserService userService;

    /**
     * JWT工具类，用于生成和验证令牌
     */
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    

    /**
     * 验证码服务，用于验证邮箱和手机验证码
     */
    @Autowired
    private VerificationCodeService verificationCodeService;

    /**
     * 通过邮箱和密码登录
     * @param email 用户邮箱
     * @param password 用户密码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByEmailPassword(String email, String password) {
        User user = userService.getUserByEmail(email);
        if (user == null || !password.equals(user.getPassword())) {
            return ResponseMessage.error(401, "邮箱或密码错误");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过邮箱和验证码登录
     * @param email 用户邮箱
     * @param code 邮箱验证码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByEmailCode(String email, String code) {
        if (!verificationCodeService.verifyEmailCode(email, code)) {
            return ResponseMessage.error(401, "邮箱验证码错误或已过期");
        }
        User user = userService.getUserByEmail(email);
        if (user == null) {
            return ResponseMessage.error(401, "用户不存在");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过手机号和密码登录
     * @param phone 用户手机号
     * @param password 用户密码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByPhonePassword(String phone, String password) {
        User user = userService.getUserByPhone(phone);
        if (user == null || !password.equals(user.getPassword())) {
            return ResponseMessage.error(401, "手机号或密码错误");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 通过手机号和验证码登录
     * @param phone 用户手机号
     * @param code 手机验证码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByPhoneCode(String phone, String code) {
        if (!verificationCodeService.verifyPhoneCode(phone, code)) {
            return ResponseMessage.error(401, "手机验证码错误或已过期");
        }
        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return ResponseMessage.error(401, "用户不存在");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 发送邮箱验证码
     * @param email 目标邮箱
     * @return 发送结果
     */
    @Override
    public ResponseMessage<?> sendEmailVerificationCode(String email) {
        verificationCodeService.sendEmailCode(email);
        return ResponseMessage.success("验证码发送成功");
    }

       /**
     * 发送手机验证码
     * @param phone 目标手机号
     * @return 发送结果
     */
    @Override
    public ResponseMessage<?> sendSmsVerificationCode(String phone) {
        verificationCodeService.sendSmsCode(phone);
        return ResponseMessage.success("短信验证码发送成功");
    }

    /**
     * 通过用户名和密码登录
     * @param username 用户名
     * @param password 用户密码
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage<?> loginByUsernamePassword(String username, String password) {
        User user = userService.getUserByUsername(username);
        if (user == null || !password.equals(user.getPassword())) {
            return ResponseMessage.error(401, "用户名或密码错误");
        }
        return generateLoginSuccessResponse(user);
    }

    /**
     * 统一登录入口，根据登录类型调用不同的登录方法
     * @param loginDTO 登录信息DTO
     * @return 登录结果，包含令牌信息或错误提示
     */
    @Override
    public ResponseMessage login(LoginDTO loginDTO) {
        if (loginDTO == null || loginDTO.getAuthType() == null) {
            return ResponseMessage.error(400, "登录参数不完整");
        }
        
        switch (loginDTO.getAuthType()) {
            case "EMAIL_PASSWORD":
                return loginByEmailPassword(loginDTO.getEmail(), loginDTO.getPassword());
            case "EMAIL_CODE":
                return loginByEmailCode(loginDTO.getEmail(), loginDTO.getCode());
            case "PHONE_PASSWORD":
                return loginByPhonePassword(loginDTO.getPhone(), loginDTO.getPassword());
            case "PHONE_CODE":
                return loginByPhoneCode(loginDTO.getPhone(), loginDTO.getCode());
            case "USERNAME_PASSWORD":
                return loginByUsernamePassword(loginDTO.getUsername(), loginDTO.getPassword());
            default:
                return ResponseMessage.error(400, "不支持的登录方式: " + loginDTO.getAuthType());
        }
    }

    /**
     * 生成登录成功响应
     * @param user 登录用户信息
     * @return 包含令牌的成功响应
     */
    private ResponseMessage<?> generateLoginSuccessResponse(User user) {
        // 检查用户是否被冻结
        if (!user.getStatus()) {
            return ResponseMessage.error(403, "账号已被冻结，无法登录");
        }
        String token = jwtTokenUtil.generateToken(user.getUsername(), user.getUserId().longValue(), Collections.singletonList(user.getRole()));
        return ResponseMessage.success(new TokenVO(token));
    }

    /**
     * 用户登出
     * @param token 用户令牌
     * @return 登出结果
     */
    @Override
    public ResponseMessage<?> logout(String token) {
        // 在实际项目中，这里应该实现令牌失效逻辑
        // 如添加到黑名单、设置过期时间等
        return ResponseMessage.success("登出成功");
    }

    /**
     * 刷新令牌
     * @param refreshToken 刷新令牌
     * @return 包含新令牌的响应
     */
    @Override
    public ResponseMessage<?> refreshToken(String refreshToken) {
        // 在实际项目中，这里应该实现刷新令牌的逻辑
        String username = jwtTokenUtil.extractUsername(refreshToken);
        User user = userService.getUserByUsername(username);
        if (user != null && jwtTokenUtil.validateToken(refreshToken, username)) {
            String newToken = jwtTokenUtil.generateToken(username, user.getUserId().longValue(), Collections.singletonList(user.getRole()));
            return ResponseMessage.success(new TokenVO(newToken));
        }
        return ResponseMessage.error(401, "刷新令牌无效");
    }

    // 内部类用于封装返回的令牌信息
    public static class TokenVO {
        private String token;

        public TokenVO(String token) {
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}