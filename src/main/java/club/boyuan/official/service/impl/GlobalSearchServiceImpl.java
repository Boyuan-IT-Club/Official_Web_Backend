package club.boyuan.official.service.impl;

import club.boyuan.official.dto.GlobalSearchDTO;
import club.boyuan.official.dto.GlobalSearchResultDTO;
import club.boyuan.official.entity.User;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.mapper.UserMapper;
import club.boyuan.official.mapper.AwardExperienceMapper;
import club.boyuan.official.service.IGlobalSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局搜索服务实现类
 */
@Service
public class GlobalSearchServiceImpl implements IGlobalSearchService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AwardExperienceMapper awardExperienceMapper;

    @Override
    public GlobalSearchResultDTO globalSearch(GlobalSearchDTO searchDTO) {
        GlobalSearchResultDTO result = new GlobalSearchResultDTO();
        String keyword = searchDTO.getKeyword();
        String searchType = searchDTO.getSearchType();

        // 如果没有指定搜索类型或指定为"all"，则搜索所有类型
        if (searchType == null || searchType.equals("all")) {
            // 搜索用户
            List<User> users = userMapper.searchUsers(keyword);
            result.setUsers(users.stream()
                    .map(GlobalSearchResultDTO.UserSearchResult::new)
                    .collect(Collectors.toList()));

            // 搜索奖项
            List<AwardExperience> awards = awardExperienceMapper.searchAwards(keyword);
            result.setAwards(awards.stream()
                    .map(GlobalSearchResultDTO.AwardSearchResult::new)
                    .collect(Collectors.toList()));
        } else if (searchType.equals("user")) {
            // 只搜索用户
            List<User> users = userMapper.searchUsers(keyword);
            result.setUsers(users.stream()
                    .map(GlobalSearchResultDTO.UserSearchResult::new)
                    .collect(Collectors.toList()));
            
            // 确保奖项列表为空
            result.setAwards(Collections.emptyList());
        } else if (searchType.equals("award")) {
            // 只搜索奖项
            List<AwardExperience> awards = awardExperienceMapper.searchAwards(keyword);
            result.setAwards(awards.stream()
                    .map(GlobalSearchResultDTO.AwardSearchResult::new)
                    .collect(Collectors.toList()));
            
            // 确保用户列表为空
            result.setUsers(Collections.emptyList());
        }

        return result;
    }
}