package club.boyuan.official.service;

import club.boyuan.official.dto.PageResultDTO;
import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IUserService {
    /*增加用户*/
    User add(UserDTO user);
    User getUserById(Integer userId);
    User edit(UserDTO user);
    void deleteUserById(Integer userId);
    List<User> getAllUsers(User currentUser);
    User getUserByUsername(String username);
    User getUserByEmail(String email);
    User getUserByPhone(String phone);
    User register(UserDTO userDTO);
    User updateUserStatus(Integer userId, String status);
    PageResultDTO<User> getUsersByConditions(String role, String dept, String status, Pageable pageable, User currentUser);
    /**
     * 更新用户头像
     * @param userId 用户ID
     * @param avatarPath 头像路径
     * @return 更新后的用户实体
     */
    User updateAvatar(Integer userId, String avatarPath) throws BusinessException;
    User updatePassword(Integer userId, String newPassword);
    User updateUserMembership(Integer userId, Boolean isMember);
}