package club.boyuan.official.domain.system.service;

import club.boyuan.official.domain.system.dto.GlobalSearchDTO;
import club.boyuan.official.domain.system.dto.GlobalSearchResultDTO;

/**
 * 全局搜索服务接口
 */
public interface IGlobalSearchService {
    /**
     * 全局搜索
     * @param searchDTO 搜索参数
     * @return 搜索结果
     */
    GlobalSearchResultDTO globalSearch(GlobalSearchDTO searchDTO);
}