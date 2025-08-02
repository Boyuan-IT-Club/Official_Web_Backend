package club.boyuan.official.service.impl;

import java.util.List;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.mapper.AwardExperienceMapper;
import club.boyuan.official.mapper.UserMapper;
import club.boyuan.official.service.IUserService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@AllArgsConstructor
public class UserServiceImpl implements IUserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    
    private final UserMapper userMapper;

    private final AwardExperienceMapper awardExperienceMapper;

    private final BCryptPasswordEncoder passwordEncoder;
    
    @Override
    public User add(UserDTO userDTO) {
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(userDTO.getUsername()) != null) {
            throw new BusinessException(BusinessExceptionEnum.USERNAME_ALREADY_EXISTS);
        }

        // 检查邮箱是否已存在
        if (userMapper.selectByEmail(userDTO.getEmail()) != null) {
            throw new BusinessException(BusinessExceptionEnum.EMAIL_ALREADY_EXISTS);
        }

        // 检查手机号是否已存在
        if (userMapper.selectByPhone(userDTO.getPhone()) != null) {
            throw new BusinessException(BusinessExceptionEnum.PHONE_ALREADY_EXISTS);
        }

        // 验证密码复杂度
        validatePasswordComplexity(userDTO.getPassword());

        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        userMapper.insert(user);
        logger.info("成功添加用户，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    public User edit(UserDTO userDTO) {
        User user = userMapper.selectById(userDTO.getUserId());
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }

        // 如果密码有更新，需要验证密码复杂度并加密
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            validatePasswordComplexity(userDTO.getPassword());
            String encodedPassword = passwordEncoder.encode(userDTO.getPassword());
            user.setPassword(encodedPassword);
        }

        // 更新其他字段
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setName(userDTO.getName());
        user.setPhone(userDTO.getPhone());
        user.setRole(userDTO.getRole());
        user.setStatus(userDTO.getStatus());
        user.setDept(userDTO.getDept());

        userMapper.updateById(user);
        logger.info("成功更新用户信息，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    public User updatePassword(Integer userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        // 验证密码复杂度
        validatePasswordComplexity(newPassword);
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userMapper.updateById(user);
        logger.info("用户ID为{}的用户密码更新成功", userId);
        return user;
    }

    @Override
    public Page<User> getUsersByConditions(String role, String dept, String status, Pageable pageable, User currentUser) {
        // 检查权限，管理员才能调用此方法
        if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        logger.debug("成功抵达Service层");
        //输出所有参数
        logger.debug("查询参数: role={}, dept={}, status={}, pageable={}, currentUser={}", role, dept, status, pageable, currentUser);

        List<User> userList = userMapper.findByRoleAndDeptAndStatus(role, dept, status, pageable);

        // 这里需要查询总记录数，假设 UserMapper 有对应的方法
        long total = userMapper.countByRoleAndDeptAndStatus(role, dept, status);
        return new PageImpl<>(userList, pageable, total);

    }

    @Override
    @Transactional
    public void deleteUserById(Integer userId) {
        if (userId == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        User user = getUserById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        // 不允许删除管理员
        if (User.ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }

        // 先删除用户的所有获奖经历
        awardExperienceMapper.deleteAwardsByUserId(userId);

        int rows = userMapper.deleteById(userId);
        if (rows <= 0) {
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR);
        }
        logger.info("成功删除用户，用户ID: {}", userId);
    }

    @Override
    public User updateUserStatus(Integer userId, String status) {
        if (userId == null || status == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        User user = getUserById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        // 不允许修改管理员状态
        if (User.ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        // 修正类型转换问题
        user.setStatus("active".equals(status));
        int rows = userMapper.updateById(user);
        if (rows <= 0) {
            throw new BusinessException(BusinessExceptionEnum.USER_INFO_UPDATE_FAILED);
        }
        logger.info("用户状态更新成功，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    public User getUserById(Integer userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public User getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    @Override
    public User getUserByPhone(String phone) {
        return userMapper.selectByPhone(phone);
    }

    @Override
    public User register(UserDTO userDTO) {
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(userDTO.getUsername()) != null) {
            throw new BusinessException(BusinessExceptionEnum.USERNAME_ALREADY_EXISTS);
        }

        // 检查邮箱是否已存在
        if (userMapper.selectByEmail(userDTO.getEmail()) != null) {
            throw new BusinessException(BusinessExceptionEnum.EMAIL_ALREADY_EXISTS);
        }

        // 检查手机号是否已存在
        if (userMapper.selectByPhone(userDTO.getPhone()) != null) {
            throw new BusinessException(BusinessExceptionEnum.PHONE_ALREADY_EXISTS);
        }

        // 验证密码复杂度
        validatePasswordComplexity(userDTO.getPassword());

        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        userMapper.insert(user);
        logger.info("用户注册成功，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    public List<User> getAllUsers(User currentUser) {
        if (currentUser == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        // 管理员可以查看所有用户，普通用户只能查看自己
        if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
            logger.debug("管理员用户查询所有用户");
            return userMapper.selectAll();
        } else {
            logger.debug("普通用户查询自己的信息，用户ID: {}", currentUser.getUserId());
            return List.of(getUserById(currentUser.getUserId()));
        }
    }

    /**
     * 验证密码复杂度
     * 密码必须包含大小写字母、数字和特殊字符中的至少三种
     *
     * @param password 密码
     * @throws BusinessException 密码不符合复杂度要求时抛出异常
     */
    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(BusinessExceptionEnum.PASSWORD_TOO_SIMPLE, "密码长度至少8位");
        }

        int complexity = 0;
        // 检查是否包含小写字母
        if (password.matches(".*[a-z].*")) {
            complexity++;
        }
        // 检查是否包含大写字母
        if (password.matches(".*[A-Z].*")) {
            complexity++;
        }
        // 检查是否包含数字
        if (password.matches(".*\\d.*")) {
            complexity++;
        }
        // 检查是否包含特殊字符
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            complexity++;
        }

        if (complexity < 3) {
            throw new BusinessException(BusinessExceptionEnum.PASSWORD_TOO_SIMPLE, 
                "密码必须包含大小写字母、数字和特殊字符中的至少三种");
        }
        
        logger.debug("密码复杂度验证通过");
    }
}