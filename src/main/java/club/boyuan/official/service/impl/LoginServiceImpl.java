package club.boyuan.official.service.impl;

import club.boyuan.official.dto.LoginDTO;
import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.User;
import club.boyuan.official.mapper.LoginMapper;
import club.boyuan.official.service.ILoginService;
import club.boyuan.official.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class LoginServiceImpl implements ILoginService {

    @Autowired
    private LoginMapper loginMapper;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    // @Autowired
    // private PasswordEncoder passwordEncoder;

    @Override
    public ResponseMessage login(LoginDTO loginDTO) {
        if (loginDTO == null || loginDTO.getUsername() == null || loginDTO.getPassword() == null) {
            return ResponseMessage.error(400, "用户名和密码不能为空");
        }

        User user = loginMapper.getUserByUsername(loginDTO.getUsername());
        if (user == null) {
            return ResponseMessage.error(401, "用户名或密码错误");
        }

        // 检查用户是否被冻结
        if (!user.getStatus()) {
            return ResponseMessage.error(403, "账号已被冻结，无法登录");
        }

        // 注释掉密码加密验证逻辑
        // if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
        //     return ResponseMessage.error(401, "用户名或密码错误");
        // }

        String token = jwtTokenUtil.generateToken(user.getUsername(), user.getUserId().longValue(), Collections.singletonList(user.getRole()));
        return ResponseMessage.success(new TokenVO(token));
    }

    @Override
    public ResponseMessage logout(String token) {
        // 在实际项目中，这里应该实现令牌失效逻辑
        // 如添加到黑名单、设置过期时间等
        return ResponseMessage.success("登出成功");
    }

    @Override
    public ResponseMessage refreshToken(String refreshToken) {
        // 在实际项目中，这里应该实现刷新令牌的逻辑
        String username = jwtTokenUtil.extractUsername(refreshToken);
        User user = loginMapper.getUserByUsername(username);
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