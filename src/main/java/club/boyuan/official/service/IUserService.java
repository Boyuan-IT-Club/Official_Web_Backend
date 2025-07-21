package club.boyuan.official.service;

import club.boyuan.official.dto.UserDTO;
import club.boyuan.official.entity.User;
import org.springframework.data.domain.Page;
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
    Page<User> getUsersByConditions(String role, String dept, String status, Pageable pageable, User currentUser);
}