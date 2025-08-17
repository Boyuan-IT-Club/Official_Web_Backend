package club.boyuan.official.controller;

import club.boyuan.official.dto.GlobalSearchDTO;
import club.boyuan.official.dto.GlobalSearchResultDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IGlobalSearchService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 全局搜索控制器
 */
@RestController
@RequestMapping("/api/search")
@AllArgsConstructor
public class GlobalSearchController {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalSearchController.class);

    @Autowired
    private IGlobalSearchService globalSearchService;
    
    private final IUserService userService;
    
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 全局搜索接口 - 仅限管理员使用
     * @param keyword 搜索关键词
     * @param searchType 搜索类型: "all" - 全部, "user" - 用户, "award" - 奖项
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果
     */
    @GetMapping("/global")
    public GlobalSearchResultDTO globalSearch(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "all") String searchType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpServletRequest request) {
        try {
            // 获取当前认证用户
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("未提供有效的认证令牌");
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            String token = authHeader.substring(7);
            String username;
            try {
                username = jwtTokenUtil.extractUsername(token);
            } catch (Exception e) {
                logger.warn("令牌解析失败: {}", e.getMessage());
                throw new BusinessException(BusinessExceptionEnum.AUTHENTICATION_FAILED);
            }
            
            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                logger.warn("用户不存在: {}", username);
                throw new BusinessException(BusinessExceptionEnum.USER_NOT_FOUND);
            }
            
            // 检查是否为管理员
            if (!User.ROLE_ADMIN.equals(currentUser.getRole())) {
                logger.warn("用户 {} 尝试访问全局搜索接口，但权限不足", username);
                throw new BusinessException(BusinessExceptionEnum.PERMISSION_DENIED);
            }
            
            // 构造搜索参数对象
            GlobalSearchDTO searchDTO = new GlobalSearchDTO();
            searchDTO.setKeyword(keyword);
            searchDTO.setSearchType(searchType);
            searchDTO.setPage(page);
            searchDTO.setSize(size);
            
            // 记录搜索日志
            logger.info("管理员 {} 执行全局搜索，关键词: {}, 类型: {}", 
                currentUser.getUsername(), 
                searchDTO.getKeyword(), 
                searchDTO.getSearchType());
            
            GlobalSearchResultDTO result = globalSearchService.globalSearch(searchDTO);
            
            logger.info("管理员 {} 搜索完成，返回 {} 个用户和 {} 个奖项结果", 
                currentUser.getUsername(), 
                result.getUsers() != null ? result.getUsers().size() : 0,
                result.getAwards() != null ? result.getAwards().size() : 0);
            
            return result;
        } catch (BusinessException e) {
            logger.warn("全局搜索业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("全局搜索发生系统异常", e);
            throw new BusinessException(BusinessExceptionEnum.SYSTEM_ERROR);
        }
    }
}