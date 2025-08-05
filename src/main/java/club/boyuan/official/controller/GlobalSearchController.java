package club.boyuan.official.controller;

import club.boyuan.official.dto.GlobalSearchDTO;
import club.boyuan.official.dto.GlobalSearchResultDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;
import club.boyuan.official.service.IGlobalSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 全局搜索控制器
 */
@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalSearchController.class);

    @Autowired
    private IGlobalSearchService globalSearchService;

    /**
     * 全局搜索接口 - 仅限管理员使用
     * @param keyword 搜索关键词
     * @param searchType 搜索类型: "all" - 全部, "user" - 用户, "award" - 奖项
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/global")
    public GlobalSearchResultDTO globalSearch(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "all") String searchType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        try {
            // 获取当前认证用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("未认证用户尝试访问全局搜索接口");
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
                authentication.getName(), 
                searchDTO.getKeyword(), 
                searchDTO.getSearchType());
            
            GlobalSearchResultDTO result = globalSearchService.globalSearch(searchDTO);
            
            logger.info("管理员 {} 搜索完成，返回 {} 个用户和 {} 个奖项结果", 
                authentication.getName(), 
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