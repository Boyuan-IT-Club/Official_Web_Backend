package club.boyuan.official.domain.user.service;

import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.user.dto.UserDTO;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IUserService extends IService<User> {
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
    /**
     * 按条件分页查用户。keyword 匹配姓名或学号(username)。
     * keyword 是后加的 —— 管理端搜索框此前发的这个参数后端不接，点了没反应。
     */
    PageResultDTO<User> getUsersByConditions(String role, String dept, String status,
                                             String keyword, Pageable pageable, User currentUser);
    /**
     * 更新用户头像
     * @param userId 用户ID
     * @param avatarPath 头像路径
     * @return 更新后的用户实体
     */
    User updateAvatar(Integer userId, String avatarPath) throws BusinessException;
    User updatePassword(Integer userId, String newPassword);
    User updateUserMembership(Integer userId, Boolean isMember);
    
    /**
     * 批量更新用户状态
     * @param userIds 用户ID列表
     * @param status 状态值 (active 或 frozen)
     * @return 更新的用户数量
     */
    int batchUpdateUserStatus(List<Integer> userIds, String status);
    
    /**
     * 批量更新用户部门
     * @param userIds 用户ID列表
     * @param dept 部门名称
     * @return 更新的用户数量
     */
    int batchUpdateUserDept(List<Integer> userIds, String dept);
    
    /**
     * 批量更新用户会员状态
     * @param userIds 用户ID列表
     * @param isMember 会员状态
     * @return 更新的用户数量
     */
    int batchUpdateUserMembership(List<Integer> userIds, Boolean isMember);

    /**
     * 管理端统计卡用的全库计数。
     */
    long countUsersByMembership(boolean isMember);

    long countFrozenUsers();
}