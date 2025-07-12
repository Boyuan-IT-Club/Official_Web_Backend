package club.boyuan.official.controller;

import club.boyuan.official.dto.LoginDTO;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private ILoginService loginService;

    @Autowired
    private IUserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

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

    @PostMapping("/login")
    public ResponseEntity<ResponseMessage<?>> login(@RequestBody LoginDTO loginDTO) {
        // 获取用户信息
        User user = userService.getUserByUsername(loginDTO.getUsername());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseMessage<>(401, "用户名或密码错误", null));
        }

        ResponseMessage<?> response = loginService.login(loginDTO);
        if (response.getCode() != 200) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ResponseMessage<>(401, "用户名或密码错误", null));
        }

        // 构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", user.getUserId());
        data.put("role", user.getRole());
        data.put("token", ((LoginServiceImpl.TokenVO) response.getData()).getToken());

        return ResponseEntity.ok(new ResponseMessage<>(200, "登录成功", data));
    }
}