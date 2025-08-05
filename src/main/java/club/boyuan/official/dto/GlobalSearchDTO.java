package club.boyuan.official.dto;

/**
 * 全局搜索参数DTO
 */
public class GlobalSearchDTO {
    private String keyword;        // 搜索关键词
    private String searchType;     // 搜索类型: "all" - 全部, "user" - 用户, "award" - 奖项
    private Integer page = 1;      // 页码
    private Integer size = 10;     // 每页大小

    // Getters and Setters
    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}