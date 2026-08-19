package club.boyuan.official.domain.user.service.impl;

import club.boyuan.official.common.converter.UserConverter;
import club.boyuan.official.common.dto.PageResultDTO;
import club.boyuan.official.domain.user.dto.UserDTO;
import club.boyuan.official.persistence.entity.Resume;
import club.boyuan.official.persistence.entity.User;
import club.boyuan.official.persistence.entity.Role;
import club.boyuan.official.persistence.entity.EvaluationSubmission;
import club.boyuan.official.persistence.mapper.EvaluationSubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import club.boyuan.official.common.exception.BusinessException;
import club.boyuan.official.common.exception.BusinessExceptionEnum;
import club.boyuan.official.persistence.entity.UserRole;
import club.boyuan.official.persistence.mapper.AwardExperienceMapper;
import club.boyuan.official.persistence.mapper.ResumeFieldValueMapper;
import club.boyuan.official.persistence.mapper.ResumeMapper;
import club.boyuan.official.persistence.mapper.UserMapper;
import club.boyuan.official.persistence.mapper.RoleMapper;
import club.boyuan.official.persistence.mapper.UserRoleMapper;
import club.boyuan.official.domain.user.service.IUserService;
import club.boyuan.official.common.utils.GitHubAccountUtil;
import club.boyuan.official.common.utils.JwtTokenUtil;
import club.boyuan.official.common.utils.PasswordValidator;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import club.boyuan.official.common.utils.PermissionUtils;

@Service
@AllArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>implements IUserService  {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final AwardExperienceMapper awardExperienceMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeFieldValueMapper resumeFieldValueMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final EvaluationSubmissionMapper evaluationSubmissionMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserConverter userConverter;

    @Override
    @Transactional
    public User add(UserDTO userDTO) {
        logger.info("开始添加新用户，用户名: {}", userDTO.getUsername());
        
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(userDTO.getUsername()) != null) {
            logger.warn("添加用户失败，用户名已存在: {}", userDTO.getUsername());
            throw new BusinessException(BusinessExceptionEnum.USERNAME_ALREADY_EXISTS);
        }

        // 检查邮箱是否已存在
        if (userMapper.selectByEmail(userDTO.getEmail()) != null) {
            logger.warn("添加用户失败，邮箱已存在: {}", userDTO.getEmail());
            throw new BusinessException(BusinessExceptionEnum.EMAIL_ALREADY_EXISTS);
        }

        // 检查手机号是否已存在
        if (userMapper.selectByPhone(userDTO.getPhone()) != null) {
            logger.warn("添加用户失败，手机号已存在: {}", userDTO.getPhone());
            throw new BusinessException(BusinessExceptionEnum.PHONE_ALREADY_EXISTS);
        }

        // GitHub 账号归一化(可选):统一为登录名,并校验未被其他用户绑定
        if (userDTO.getGithub() != null && !userDTO.getGithub().isBlank()) {
            String normalizedGithub = GitHubAccountUtil.normalize(userDTO.getGithub());
            GitHubAccountUtil.assertNotBound(userMapper, normalizedGithub, null);
            userDTO.setGithub(normalizedGithub);
        }

        // 验证密码复杂度
        PasswordValidator.validate(userDTO.getPassword());

        User user = userConverter.toEntity(userDTO);
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        user.setRole("APPLICANT");
        userMapper.insert(user);
        
        // 注：不再自动分配 RBAC 默认角色，新用户为「暂无角色」，由管理员按需分配
        // （user 表的 role 列为 NOT NULL 遗留字段，仅写入占位值，界面不展示）
        logger.info("成功添加用户，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    public User edit(UserDTO userDTO) {
        User user = userMapper.selectById(userDTO.getUserId());
        if (user == null) {
            logger.warn("尝试更新不存在的用户，用户ID: {}", userDTO.getUserId());
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        
        logger.info("开始更新用户信息，用户ID: {}", userDTO.getUserId());
        logger.debug("更新前的用户信息: username={}, email={}, name={}, phone={}, deptId={}", 
                    user.getUsername(), user.getEmail(), user.getName(), user.getPhone(), user.getDeptId());

        // 如果密码有更新，需要验证密码复杂度并加密
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            logger.debug("检测到密码更新请求，用户ID: {}", userDTO.getUserId());
            PasswordValidator.validate(userDTO.getPassword());
            String encodedPassword = passwordEncoder.encode(userDTO.getPassword());
            user.setPassword(encodedPassword);
            logger.info("用户密码更新成功，用户ID: {}", userDTO.getUserId());
        } else {
            logger.debug("未检测到密码更新请求，保持原密码不变，用户ID: {}", userDTO.getUserId());
        }

        // 更新其他字段，但不更新状态等敏感字段
        if (userDTO.getUsername() != null) {
            user.setUsername(userDTO.getUsername());
            logger.debug("用户名更新为: {}", userDTO.getUsername());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
            logger.debug("邮箱更新为: {}", userDTO.getEmail());
        }
        if (userDTO.getName() != null) {
            user.setName(userDTO.getName());
            logger.debug("姓名更新为: {}", userDTO.getName());
        }
        if (userDTO.getPhone() != null) {
            user.setPhone(userDTO.getPhone());
            logger.debug("电话更新为: {}", userDTO.getPhone());
        }
        if (userDTO.getDeptId() != null) {
            user.setDeptId(userDTO.getDeptId());
            logger.debug("部门更新为: {}", userDTO.getDeptId());
        }
        if (userDTO.getMajor() != null) {
            user.setMajor(userDTO.getMajor());
            logger.debug("专业更新为: {}", userDTO.getMajor());
        }
        if (userDTO.getGithub() != null) {
            String normalizedGithub = GitHubAccountUtil.normalize(userDTO.getGithub());
            if (normalizedGithub == null) {
                // updateById 默认忽略 null 字段(setGithub(null) 不会进 UPDATE SQL,线上事故:解绑 200 但值不变);
                // 显式 UPDATE ... SET github = NULL 才能真正解绑
                userMapper.update(null, new UpdateWrapper<User>()
                        .eq("user_id", user.getUserId())
                        .set("github", null));
                user.setGithub(null);
                logger.debug("GitHub账号已解绑，用户ID: {}", userDTO.getUserId());
            } else {
                GitHubAccountUtil.assertNotBound(userMapper, normalizedGithub, userDTO.getUserId());
                userDTO.setGithub(normalizedGithub);
                user.setGithub(normalizedGithub);
                logger.debug("GitHub账号更新为: {}", normalizedGithub);
                // 回填认领:该 github 的历史未认领提交归属到用户
                EvaluationSubmission patch = new EvaluationSubmission();
                patch.setUserId(userDTO.getUserId());
                evaluationSubmissionMapper.update(patch, new LambdaUpdateWrapper<EvaluationSubmission>()
                        .eq(EvaluationSubmission::getGithubUsername, normalizedGithub)
                        .isNull(EvaluationSubmission::getUserId));
                logger.info("绑定 GitHub {} 后回填认领未认领提交,用户ID: {}", normalizedGithub, userDTO.getUserId());
            }
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
            logger.debug("头像更新为: {}", userDTO.getAvatar());
        }
//        // 允许更新用户角色（用于管理员权限授予）
//        if (userDTO.getRole() != null) {
//            user.setRole(userDTO.getRole());
//            logger.debug("角色更新为: {}", userDTO.getRole());
//        }
        // 不允许通过edit方法修改用户状态
        // user.setStatus(userDTO.getStatus());

        userMapper.updateById(user);
        logger.info("成功更新用户信息，用户ID: {}", user.getUserId());
        logger.debug("更新后的用户信息: username={}, email={}, name={}, phone={}, deptId={}", 
                    user.getUsername(), user.getEmail(), user.getName(), user.getPhone(), user.getDeptId());
        return user;
    }

    @Override
    public User updatePassword(Integer userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        // 验证密码复杂度
        PasswordValidator.validate(newPassword);
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);
        userMapper.updateById(user);
        logger.info("用户ID为{}的用户密码更新成功", userId);
        return user;
    }

    @Override
    public PageResultDTO<User> getUsersByConditions(String role, String dept, String status, Pageable pageable, User currentUser) {
        // 权限检查由调用方的 @PreAuthorize 注解统一管理
        
        // 查询总记录数
        long totalElements = userMapper.countByRoleAndDeptAndStatus(role, dept, status);
        
        // 计算总页数
        int pageSize = pageable.getPageSize();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        
        // 如果请求的页码超过了最大页码，则返回空列表
        int pageNumber = pageable.getPageNumber();
        if (totalPages > 0 && pageNumber >= totalPages) {
            // 返回空列表而不是错误，避免前端出现异常
            return new PageResultDTO<>(List.of(), totalElements, totalPages, pageNumber, pageSize, pageNumber == 0, pageNumber >= totalPages - 1);
        }
        
        List<User> userList = userMapper.findByRoleAndDeptAndStatus(role, dept, status, pageable);
        boolean isFirst = pageNumber == 0;
        boolean isLast = pageNumber >= totalPages - 1;
        return new PageResultDTO<>(userList, totalElements, totalPages, pageNumber, pageSize, isFirst, isLast);
    }

    @Override
    @Transactional
    public void deleteUserById(Integer userId) {
        logger.info("开始删除用户，用户ID: {}", userId);
        
        if (userId == null) {
            logger.warn("删除用户失败，用户ID不能为空");
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        
        User user = getUserById(userId);
        if (user == null) {
            logger.warn("删除用户失败，用户不存在，用户ID: {}", userId);
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        
        // 不允许删除管理员
        if (PermissionUtils.hasAdminRole(user)) {
            logger.warn("删除用户失败，不允许删除管理员用户，用户ID: {}", userId);
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }

        try {
            // 先删除用户的所有简历相关的字段值
            List<Resume> resumes = resumeMapper.findByUserId(userId);
            for (Resume resume : resumes) {
                // 删除简历相关的字段值
                resumeFieldValueMapper.deleteByResumeId(resume.getResumeId());
            }

            // 删除用户的所有简历
            for (Resume resume : resumes) {
                resumeMapper.deleteById(resume.getResumeId());
            }

            // 删除用户的所有获奖经历
            awardExperienceMapper.deleteAwardsByUserId(userId);

            // 删除用户的角色关联
            userRoleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserRole>()
                    .eq("user_id", userId));

            // 删除用户本身
            int rows = userMapper.deleteById(userId);
            if (rows <= 0) {
                logger.error("删除用户失败，数据库操作异常，用户ID: {}", userId);
                throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR);
            }
            logger.info("成功删除用户，用户ID: {}", userId);
        } catch (Exception e) {
            logger.error("删除用户失败，用户ID: {}", userId, e);
            throw new RuntimeException("删除用户失败: " + e.getMessage(), e);
        }
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
        if (PermissionUtils.hasAdminRole(user)) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        // 修正类型转换问题，setStatus方法接受Integer类型
        user.setStatus("active".equals(status) ? 1 : 0);
        int rows = userMapper.updateById(user);
        if (rows <= 0) {
            throw new BusinessException(BusinessExceptionEnum.USER_INFO_UPDATE_FAILED);
        }
        logger.info("用户状态更新成功，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    @Transactional
    public User updateUserMembership(Integer userId, Boolean isMember) {
        if (userId == null || isMember == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        User user = getUserById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }
        // 不允许修改管理员会员状态
        if (PermissionUtils.hasAdminRole(user)) {
            throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
        }
        
        // 这里原先注释成「User 类已经没有 isMember 字段」并直接 updateById(user)，
        // 等于什么都没改却返回成功 —— 单个用户的录取/取消录取接口一直是空操作。
        // 实体现已恢复 isMember 字段（见 V20 与 UserResultMap 的对齐），显式赋值再落库。
        user.setIsMember(isMember);
        int rows = userMapper.updateById(user);
        if (rows <= 0) {
            throw new BusinessException(BusinessExceptionEnum.USER_INFO_UPDATE_FAILED);
        }
        // 社员身份与社员角色一并同步，与批量路径保持一致
        syncMemberRole(List.of(userId), Boolean.TRUE.equals(isMember));
        logger.info("用户会员状态更新成功，用户ID: {}，会员状态: {}", user.getUserId(), isMember);
        return user;
    }

    @Override
    @Transactional
    public int batchUpdateUserStatus(List<Integer> userIds, String status) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        
        if (status == null || (!"active".equals(status) && !"frozen".equals(status))) {
            throw new BusinessException(BusinessExceptionEnum.PARAMETER_VALIDATION_FAILED, "无效的状态值");
        }
        
        // 检查用户是否存在且不是管理员
        List<User> users = userMapper.selectByIds(userIds);
        for (User user : users) {
            if (user == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            // 不允许修改管理员状态
            if (PermissionUtils.hasAdminRole(user)) {
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED, "不允许批量修改管理员状态");
            }
        }
        
        int updatedCount = userMapper.batchUpdateStatusByIds(userIds, "active".equals(status));
        logger.info("批量更新用户状态成功，更新数量: {}，状态: {}", updatedCount, status);
        return updatedCount;
    }

    @Override
    @Transactional
    public int batchUpdateUserDept(List<Integer> userIds, String dept) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        
        if (dept == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "部门信息不能为空");
        }
        
        // 检查用户是否存在且不是管理员
        List<User> users = userMapper.selectByIds(userIds);
        for (User user : users) {
            if (user == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            // 不允许修改管理员信息
            if (PermissionUtils.hasAdminRole(user)) {
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED, "不允许批量修改管理员信息");
            }
        }
        
        int updatedCount = userMapper.batchUpdateDeptByIds(userIds, dept);
        logger.info("批量更新用户部门成功，更新数量: {}，部门: {}", updatedCount, dept);
        return updatedCount;
    }

    @Override
    @Transactional
    public int batchUpdateUserMembership(List<Integer> userIds, Boolean isMember) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        
        if (isMember == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD, "会员状态不能为空");
        }
        
        // 检查用户是否存在且不是管理员
        List<User> users = userMapper.selectByIds(userIds);
        for (User user : users) {
            if (user == null) {
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            // 不允许修改管理员会员状态
            if (PermissionUtils.hasAdminRole(user)) {
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED, "不允许批量修改管理员会员状态");
            }
        }
        
        int updatedCount = userMapper.batchUpdateMembershipByIds(userIds, isMember);
        // 社员身份与社员角色一并同步，见 syncMemberRole 的说明
        syncMemberRole(userIds, Boolean.TRUE.equals(isMember));
        logger.info("批量更新用户会员状态成功，更新数量: {}，会员状态: {}", updatedCount, isMember);
        return updatedCount;
    }

    /**
     * 让「社员身份」与「社员角色」保持一致。
     *
     * 此前这两者完全独立:批量录取只写 user.is_member 标记,不动 user_role。
     * 结果是管理员点了「录取为社员」,对方并不会因此获得社员角色的任何权限,
     * 而界面上看不出区别 —— 按业务要求现在一并同步。
     *
     * 按 role_code 查角色而不是写死 role_id:V6 种子里 MEMBER 是 3,但显式 ID
     * 正是 V10/V13 权限撞号的根因,这里不重复那个做法。
     * 角色行不存在时只记日志不抛异常 —— 录取本身已经成功,不该因为角色缺失而整体回滚。
     */
    private void syncMemberRole(List<Integer> userIds, boolean isMember) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Role memberRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, "MEMBER").last("LIMIT 1"));
        if (memberRole == null) {
            logger.warn("未找到 role_code = MEMBER 的角色，跳过社员角色同步，用户数: {}", userIds.size());
            return;
        }

        if (isMember) {
            for (Integer userId : userIds) {
                boolean exists = userRoleMapper.exists(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, memberRole.getRoleId()));
                if (!exists) {
                    UserRole ur = new UserRole();
                    ur.setUserId(userId);
                    ur.setRoleId(memberRole.getRoleId());
                    userRoleMapper.insert(ur);
                }
            }
            logger.info("已为 {} 个用户同步社员角色", userIds.size());
        } else {
            int removed = userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                    .in(UserRole::getUserId, userIds)
                    .eq(UserRole::getRoleId, memberRole.getRoleId()));
            logger.info("开除社员：已移除 {} 条社员角色绑定", removed);
        }
    }

    @Override
    public long countUsersByMembership(boolean isMember) {
        return userMapper.countByMembership(isMember);
    }

    @Override
    public long countFrozenUsers() {
        return userMapper.countFrozen();
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
    public User updateAvatar(Integer userId, String avatarPath) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
        }

        // 只更新头像字段，不涉及密码
        user.setAvatar(avatarPath);
        userMapper.updateById(user);
        logger.info("成功更新用户头像，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    @Transactional
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

        User user = userConverter.toEntity(userDTO);
        // 使用BCrypt加密密码
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        user.setRole("APPLICANT");
        userMapper.insert(user);
        
        // 注：不再自动分配 RBAC 默认角色，新用户为「暂无角色」，由管理员按需分配
        // （user 表的 role 列为 NOT NULL 遗留字段，仅写入占位值，界面不展示）
        logger.info("用户注册成功，用户ID: {}", user.getUserId());
        return user;
    }

    @Override
    public List<User> getAllUsers(User currentUser) {
        if (currentUser == null) {
            throw new BusinessException(BusinessExceptionEnum.MISSING_REQUIRED_FIELD);
        }
        // 管理员可以查看所有用户，普通用户只能查看自己
        boolean hasManagePermission = false;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            hasManagePermission = SecurityContextHolder.getContext().getAuthentication()
                    .getAuthorities().stream()
                    .anyMatch(auth -> "admin:manage".equals(auth.getAuthority()));
        }
        
        if (hasManagePermission) {
            // 移除过多的 debug 日志
            return userMapper.selectAll();
        } else {
            // 移除过多的 debug 日志
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
        PasswordValidator.validate(password);
    }
}