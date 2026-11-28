package club.boyuan.official.domain.interview.service;

import club.boyuan.official.domain.interview.dto.InterviewResultListItem;
import club.boyuan.official.persistence.projection.InterviewResultListRow;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InterviewResultListConverter {
    InterviewResultListItem toItem(InterviewResultListRow row);
}
