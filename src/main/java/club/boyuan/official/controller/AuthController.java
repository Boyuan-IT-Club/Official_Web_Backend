package club.boyuan.official.controller;

import club.boyuan.official.dto.AuthLoginDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.ILoginService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.impl.LoginServiceImpl;
import club.boyuan.official.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import club.boyuan.official.dto.RegisterDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证控制器
 * 处理用户注册、登录、验证码发送等认证相关请求
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private ILoginService loginService;

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    /**
     * 用户注册接口
     * @param registerDTO 注册信息DTO，包含用户名、密码、邮箱、手机号等
     * @return 注册结果，包含用户ID和用户名
     */
    @PostMapping("/register")
    public ResponseEntity<ResponseMessage<?>> register(@Valid @RequestBody RegisterDTO registerDTO) {
        // 检查用户名是否已存在
        if (userService.getUserByUsername(registerDTO.getUsername()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseMessage<>(409, "用户名已存在", null));
        }

        // 检查邮箱是否已存在
        if (userService.getUserByEmail(registerDTO.getEmail()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseMessage<>(409, "邮箱已存在", null));
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
        ResponseMessage<?> response = loginService.sendEmailVerificationCode(email);
        return ResponseEntity.status(response.getCode() == 200 ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(response);
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
        ResponseMessage<?> response = loginService.sendSmsVerificationCode(phone);
        return ResponseEntity.status(response.getCode() == 200 ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 用户登录接口，支持多种认证方式
     * @param authLoginDTO 登录信息DTO，包含auth_type和对应认证信息
     * auth_type支持：email-password、email-code、phone-password、phone-code、username-password
     * @return 登录结果，包含用户ID、角色和JWT令牌
     */
    @PostMapping("/login")
    public ResponseEntity<ResponseMessage<?>> login(@Valid @RequestBody AuthLoginDTO authLoginDTO) {
        User user = null;
        ResponseMessage<?> response = null;

        switch (authLoginDTO.getAuth_type()) {
            case "email-password":
                user = userService.getUserByEmail(authLoginDTO.getAuth_id());
                response = loginService.loginByEmailPassword(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "email-code":
                user = userService.getUserByEmail(authLoginDTO.getAuth_id());
                response = loginService.loginByEmailCode(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "phone-password":
                user = userService.getUserByPhone(authLoginDTO.getAuth_id());
                response = loginService.loginByPhonePassword(authLoginDTO.getAuth_id(), authLoginDTO.getVerify());
                break;
            case "phone-code":
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