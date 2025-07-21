package club.boyuan.official.controller;

import club.boyuan.official.dto.AuthLoginDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.ILoginService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import club.boyuan.official.utils.MessageUtils;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import club.boyuan.official.dto.RegisterDTO;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 处理用户注册、登录、验证码发送等认证相关请求
 */
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private final ILoginService loginService;

    private final IUserService userService;

    private final JwtTokenUtil jwtTokenUtil;

    private final MessageUtils messageUtils;

    /**
     * 用户注册接口
     * @param registerDTO 注册信息DTO，包含用户名、密码、邮箱、手机号等
     * @return 注册结果，包含用户ID和用户名
     */
    @PostMapping("/register")
    public ResponseEntity<ResponseMessage<?>> register(@Valid @RequestBody RegisterDTO registerDTO) {
        // 使用工具类验证邮箱和手机号格式
        messageUtils.validateEmail(registerDTO.getEmail());
        if (!registerDTO.getEmail().endsWith("@stu.ecnu.edu.cn")) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_EMAIL_FORMAT);
        }
        messageUtils.validatePhone(registerDTO.getPhone());

        // 检查用户名是否已存在
        if (userService.getUserByUsername(registerDTO.getUsername()) != null) {
            throw new BusinessException(BusinessExceptionEnum.USERNAME_ALREADY_EXISTS);
        }

        // 检查邮箱是否已存在
        if (userService.getUserByEmail(registerDTO.getEmail()) != null) {
            throw new BusinessException(BusinessExceptionEnum.EMAIL_ALREADY_EXISTS);
        }

        // 创建用户DTO并设置基本信息
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(registerDTO.getUsername());
        userDTO.setPassword(registerDTO.getPassword());
        userDTO.setEmail(registerDTO.getEmail());
        userDTO.setPhone(registerDTO.getPhone());
        userDTO.setName(registerDTO.getName());
        userDTO.setRole(User.ROLE_USER); // 默认普通用户角色
        userDTO.setStatus(true); // 默认激活状态

        User newUser = userService.register(userDTO);
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", newUser.getUserId());
        data.put("username", newUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseMessage<>(201, "注册成功", data));
    }

    /**
     * 发送邮箱验证码
     * @param request 请求参数，包含email字段
     * @return 验证码发送结果
     */
    @PostMapping("/send-email-code")
    public ResponseEntity<ResponseMessage<?>> sendEmailCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(400, "邮箱不能为空", null));
        }
        messageUtils.validateEmail(email);
        if (!email.endsWith("@stu.ecnu.edu.cn")) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_EMAIL_FORMAT);
        }
        // 使用工具类发送邮箱验证码
        String code = loginService.generateVerificationCode("email");
        loginService.saveVerificationCode(email, code, 300000);
        String content = "您的验证码是：" + code + "，有效期5分钟";
        messageUtils.sendEmail(email, "邮箱验证码", content);
        return ResponseEntity.ok(new ResponseMessage<>(200, "验证码发送成功", null));
    }

    /**
     * 发送手机验证码
     * @param request 请求参数，包含phone字段
     * @return 验证码发送结果
     */
    @PostMapping("/send-sms-code")
    public ResponseEntity<ResponseMessage<?>> sendSmsCode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseMessage<>(400, "手机号不能为空", null));
        }
        messageUtils.validatePhone(phone);
        String code = loginService.generateVerificationCode("sms");
        loginService.saveVerificationCode(phone, code, 300000);
        messageUtils.sendSms(phone);
        return ResponseEntity.ok(new ResponseMessage<>(200, "验证码发送成功", null));
    }

    /**
     * 用户登录接口，支持多种认证方式
     * @param authLoginDTO 登录信息DTO，包含auth_type和对应认证信息
     * auth_type支持：email-password、email-code、phone-password、phone-code、username-password
     * @return 登录结果，包含用户ID、角色和JWT令牌
     */
    @PostMapping("/login")
    public ResponseEntity<ResponseMessage<?>> login(@Valid @RequestBody AuthLoginDTO authLoginDTO) {
        User user;
        ResponseMessage<?> response;

        switch (authLoginDTO.getAuth_type()) {
            case "email-password":
                // 验证邮箱格式
                messageUtils.validateEmail(authLoginDTO.getAuth_id());
                if (!authLoginDTO.getAuth_id().endsWith("@stu.ecnu.edu.cn")) {
                    throw new BusinessException(BusinessExceptionEnum.INVALID_EMAIL_FORMAT);
                }
                user = userService.getUserByEmail(authLoginDTO.getAuth_id());
                response = loginService.loginByEmailPassword(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "email-code":
                // 验证邮箱格式
                messageUtils.validateEmail(authLoginDTO.getAuth_id());
                if (!authLoginDTO.getAuth_id().endsWith("@stu.ecnu.edu.cn")) {
                    throw new BusinessException(BusinessExceptionEnum.INVALID_EMAIL_FORMAT);
                }
                user = userService.getUserByEmail(authLoginDTO.getAuth_id());
                response = loginService.loginByEmailCode(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "phone-password":
                // 验证手机号格式
                messageUtils.validatePhone(authLoginDTO.getAuth_id());
                user = userService.getUserByPhone(authLoginDTO.getAuth_id());
                response = loginService.loginByPhonePassword(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "phone-code":
                // 验证手机号格式
                messageUtils.validatePhone(authLoginDTO.getAuth_id());
                user = userService.getUserByPhone(authLoginDTO.getAuth_id());
                response = loginService.loginByPhoneCode(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "username-password":
                user = userService.getUserByUsername(authLoginDTO.getAuth_id());
                response = loginService.loginByUsernamePassword(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            default:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseMessage<>(400, "不支持的认证方式", null));
        }

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseMessage<>(401, "用户不存在", null));
        }

        if (response == null || response.getCode() != 200) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseMessage<>(401, response != null ? response.getMessage() : "登录失败", null));
        }

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", user.getUserId());
        data.put("role", user.getRole());
        data.put("token", jwtTokenUtil.generateToken(user.getUsername()));

        return ResponseEntity.ok(new ResponseMessage<>(200, "登录成功", data));
    }
}