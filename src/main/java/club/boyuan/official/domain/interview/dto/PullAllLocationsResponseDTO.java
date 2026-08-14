package club.boyuan.official.domain.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 一键拉回全部地点的提交结果。
 *
 * 刻意保持「一张表一个任务」：拉回的进度、失败行、错误信息天然是按表格分开的，
 * 把 N 张表塞进一个任务会让进度和报错混在一起，出问题时分不清是哪个地点的表有问题。
 */
@Data
@Accessors(chain = true)
public class PullAllLocationsResponseDTO {

    private List<LocationTask> tasks;

    /** 未配置链接因而没有提交任务的地点 */
    private List<String> skippedLocations;

    @Data
    @AllArgsConstructor
    public static class LocationTask {
        private String location;
        private Long taskId;
    }
}
