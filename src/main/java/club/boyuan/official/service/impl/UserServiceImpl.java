package club.boyuan.official.service.impl;

import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.mapper.AwardExperienceMapper;
import club.boyuan.official.mapper.UserMapper;
import club.boyuan.official.service.IUserService;
import lombok.AllArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
@AllArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserMapper userMapper;

    private final AwardExperienceMapper awardExperienceMapper;

    @Override
    public Page<User> getUsersByConditions(String role, String dept, String status, Pageable pageable, User currentUser) {
        // 检查权限，管理员才能调用此方法
        if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
            throw new IllegalArgumentException("只有管理员可以按条件查询用户");
        }
        System.out.println("成功抵达Service层");
        //输出所有参数
        System.out.println("role: " + role);
        System.out.println("dept: " + dept);
        System.out.println("status: " + status);
        System.out.println("pageable: " + pageable);
        System.out.println("currentUser: " + currentUser);

        List<User> userList = userMapper.findByRoleAndDeptAndStatus(role, dept, status, pageable);
        // 这里需要查询总记录数，假设 UserMapper 有对应的方法
        long total = userMapper.countByRoleAndDeptAndStatus(role, dept, status);
        return new PageImpl<>(userList, pageable, total);

    }

    @Override
    public User getUserById(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userMapper.selectById(userId);
    }

    @Override
    public User getUserByUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return userMapper.selectByUsername(username);
    }

    @Override
    public User getUserByPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        return userMapper.selectByPhone(phone);
    }

    @Override
    public User getUserByEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return userMapper.selectByEmail(email);
    }


    @Override
    @Transactional
    public User register(UserDTO userDTO) {
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(userDTO.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 检查邮箱是否已存在
        if (userMapper.selectByEmail(userDTO.getEmail()) != null) {
            throw new IllegalArgumentException("邮箱已存在");
        }

        User newUser = new User();
        newUser.setUsername(userDTO.getUsername());
        newUser.setPassword(userDTO.getPassword());
        newUser.setEmail(userDTO.getEmail());
        newUser.setName(userDTO.getName());
        newUser.setPhone(userDTO.getPhone());
        newUser.setRole(userDTO.getRole());
        newUser.setStatus(true); // 默认激活状态

        int rows = userMapper.insert(newUser);
        return rows > 0 ? newUser : null;
    }
    
    /*
    private String encryptPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(password.getBytes());
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }
    */

    @Override
    @Transactional
    public User add(UserDTO user) {
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User newUser = new User();
        BeanUtils.copyProperties(user, newUser);
        newUser.setStatus(true); // 设置默认激活状态
        int rows = userMapper.insert(newUser);
        return rows > 0 ? newUser : null;
    }

    @Override
    @Transactional
    public User edit(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        User existingUser = getUserById(userDTO.getUserId());
        if (existingUser == null) {
            throw new IllegalArgumentException("用户不存在: " + userDTO.getUserId());
        }
        BeanUtils.copyProperties(userDTO, existingUser);
        int rows = userMapper.updateById(existingUser);
        if (rows <= 0) {
            throw new RuntimeException("更新用户失败");
        }
        return existingUser;
    }

    @Override
    @Transactional
    public void deleteUserById(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        User user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        // 不允许删除管理员
        if (User.ROLE_ADMIN.equals(user.getRole())) {
            throw new IllegalArgumentException("不能删除管理员用户");
        }

        // 先删除用户的所有获奖经历
        awardExperienceMapper.deleteAwardsByUserId(userId);

        int rows = userMapper.deleteById(userId);
        if (rows <= 0) {
            throw new RuntimeException("删除用户失败");
        }
    }

    @Override
    public User updateUserStatus(Integer userId, String status) {
        if (userId == null || status == null) {
            throw new IllegalArgumentException("用户ID和状态不能为空");
        }
        User user = getUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        // 不允许修改管理员状态
        if (User.ROLE_ADMIN.equals(user.getRole())) {
            throw new IllegalArgumentException("不能修改管理员用户状态");
        }
        // 修正类型转换问题
        user.setStatus("active".equals(status));
       int rows = userMapper.updateById(user);
       if (rows <= 0) {
           throw new RuntimeException("更新用户状态失败");
       }
        return user;
    }

    @Override
    public List<User> getAllUsers(User currentUser) {
        if (currentUser == null) {
            throw new IllegalArgumentException("当前用户信息不能为空");
        }
        // 管理员可以查看所有用户，普通用户只能查看自己
        if (User.ROLE_ADMIN.equals(currentUser.getRole())) {
            return userMapper.selectAll();
        } else {
            return List.of(getUserById(currentUser.getUserId()));
        }
    }
}