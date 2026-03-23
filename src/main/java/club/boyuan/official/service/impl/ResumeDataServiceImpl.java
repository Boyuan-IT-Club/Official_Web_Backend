package club.boyuan.official.service.impl;

import club.boyuan.official.entity.Resume;
import club.boyuan.official.entity.ResumeFieldDefinition;
import club.boyuan.official.entity.ResumeFieldValue;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IResumeFieldDefinitionService;
import club.boyuan.official.service.IResumeService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.service.ResumeDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 简历数据服务实现类
 * 专门处理简历字段定义和字段值解析相关功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeDataServiceImpl implements ResumeDataService {

    private final IResumeService resumeService;
    private final IResumeFieldDefinitionService resumeFieldDefinitionService;
    private final IUserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public ResumeFieldDefinition getInterviewTimeFieldDefinition(Integer cycleId) {
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        return fieldDefinitions.stream()
                .filter(field -> "期望的面试时间".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public ResumeFieldDefinition getExpectedDepartmentsFieldDefinition(Integer cycleId) {
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(cycleId);
        return fieldDefinitions.stream()
                .filter(field -> "期望部门".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<Integer, List<String>> getUserPreferredTimes(List<Resume> resumes, Integer fieldId) {
        Map<Integer, List<String>> userPreferredTimes = new HashMap<>();

        for (Resume resume : resumes) {
            List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
            // 获取所有匹配的字段值
            List<ResumeFieldValue> interviewTimeValues = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .collect(Collectors.toList());

            log.info("用户 {} 的期望面试时间字段值数量: {}", resume.getUserId(), interviewTimeValues.size());

            if (!interviewTimeValues.isEmpty()) {
                List<String> allPreferredTimes = new ArrayList<>();
                for (ResumeFieldValue interviewTimeValue : interviewTimeValues) {
                    log.info("用户 {} 的期望面试时间字段值: {}", resume.getUserId(), interviewTimeValue.getFieldValue());
                    try {
                        // 检查是否是包含first和second字段的JSON对象格式
                        String fieldValue = interviewTimeValue.getFieldValue();
                        log.debug("解析用户 {} 的期望面试时间字段值: {}", resume.getUserId(), fieldValue);

                        if (fieldValue != null && fieldValue.contains("\"first\"") && fieldValue.contains("\"second\"")) {
                            // 解析包含first和second字段的JSON对象
                            JsonNode jsonNode = objectMapper.readTree(fieldValue);
                            String first = jsonNode.has("first") ? jsonNode.get("first").asText() : null;
                            String second = jsonNode.has("second") ? jsonNode.get("second").asText() : null;

                            log.debug("用户 {} 的first字段: {}, second字段: {}", resume.getUserId(), first, second);

                            // 添加非空的时间选项
                            if (first != null && !first.isEmpty() && !"null".equals(first)) {
                                allPreferredTimes.add(first);
                            }
                            if (second != null && !second.isEmpty() && !"null".equals(second)) {
                                allPreferredTimes.add(second);
                            }
                        } else {
                            // 解析普通的JSON数组格式（向后兼容）
                            List<String> preferredTimes = objectMapper.readValue(fieldValue, new TypeReference<List<String>>() {});
                            allPreferredTimes.addAll(preferredTimes);
                        }

                        log.debug("用户 {} 添加的期望时间: {}", resume.getUserId(), allPreferredTimes);
                    } catch (Exception e) {
                        log.warn("解析用户 {} 的期望面试时间失败: {}", resume.getUserId(),
                                interviewTimeValue.getFieldValue(), e);
                    }
                }
                log.info("用户 {} 解析后的所有期望面试时间: {}", resume.getUserId(), allPreferredTimes);
                userPreferredTimes.put(resume.getUserId(), allPreferredTimes);
            }
        }

        log.info("总共解析了 {} 个用户的期望面试时间", userPreferredTimes.size());
        return userPreferredTimes;
    }

    @Override
    public Map<Integer, List<String>> getUserPreferredDepartments(List<Resume> resumes, Integer fieldId) {
        Map<Integer, List<String>> userPreferredDepartments = new HashMap<>();

        for (Resume resume : resumes) {
            List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());
            // 获取所有匹配的字段值
            List<ResumeFieldValue> expectedDepartmentsValues = fieldValues.stream()
                    .filter(value -> fieldId.equals(value.getFieldId()))
                    .collect(Collectors.toList());

            log.info("用户 {} 的期望部门字段值数量: {}", resume.getUserId(), expectedDepartmentsValues.size());

            if (!expectedDepartmentsValues.isEmpty()) {
                List<String> allPreferredDepartments = new ArrayList<>();
                for (ResumeFieldValue expectedDepartmentsValue : expectedDepartmentsValues) {
                    log.info("用户 {} 的期望部门字段值: {}", resume.getUserId(), expectedDepartmentsValue.getFieldValue());
                    try {
                        // 解析JSON数组
                        List<String> preferredDepartments = objectMapper.readValue(
                                expectedDepartmentsValue.getFieldValue(),
                                new TypeReference<List<String>>() {});
                        allPreferredDepartments.addAll(preferredDepartments);
                    } catch (Exception e) {
                        log.warn("解析用户 {} 的期望部门失败: {}", resume.getUserId(),
                                expectedDepartmentsValue.getFieldValue(), e);
                    }
                }
                log.info("用户 {} 解析后的所有期望部门: {}", resume.getUserId(), allPreferredDepartments);
                userPreferredDepartments.put(resume.getUserId(), allPreferredDepartments);
            }
        }

        return userPreferredDepartments;
    }

    @Override
    public String getResumeName(Resume resume) {
        // 获取简历中的所有字段值
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());

        // 获取当前周期的字段定义
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());

        // 查找姓名字段定义
        ResumeFieldDefinition nameFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "姓名".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);

        // 如果找到了姓名字段定义，则查找对应的字段值
        if (nameFieldDefinition != null) {
            String name = fieldValues.stream()
                    .filter(value -> nameFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);

            if (name != null && !name.isEmpty()) {
                return name;
            }
        }

        // 如果简历中没有姓名字段或为空，则使用用户表中的姓名
        User user = userService.getUserById(resume.getUserId());
        return user != null ? user.getName() : "";
    }

    @Override
    public String getResumeEmail(Resume resume) {
        // 获取简历中的所有字段值
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());

        // 获取当前周期的字段定义
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());

        // 查找邮箱字段定义
        ResumeFieldDefinition emailFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "邮箱".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);

        // 如果找到了邮箱字段定义，则查找对应的字段值
        if (emailFieldDefinition != null) {
            String email = fieldValues.stream()
                    .filter(value -> emailFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);

            if (email != null && !email.isEmpty()) {
                return email;
            }
        }

        // 如果简历中没有邮箱字段或为空，则使用用户表中的邮箱
        User user = userService.getUserById(resume.getUserId());
        return user != null ? user.getEmail() : "";
    }

    @Override
    public String getResumeMajor(Resume resume) {
        // 获取简历中的所有字段值
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());

        // 获取当前周期的字段定义
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());

        // 查找专业字段定义
        ResumeFieldDefinition majorFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "专业".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);

        // 如果找到了专业字段定义，则查找对应的字段值
        if (majorFieldDefinition != null) {
            String major = fieldValues.stream()
                    .filter(value -> majorFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);

            if (major != null && !major.isEmpty()) {
                return major;
            }
        }

        // 如果简历中没有专业字段或为空，则返回空字符串
        return "";
    }

    @Override
    public String getResumeGrade(Resume resume) {
        // 获取简历中的所有字段值
        List<ResumeFieldValue> fieldValues = resumeService.getFieldValuesByResumeId(resume.getResumeId());

        // 获取当前周期的字段定义
        List<ResumeFieldDefinition> fieldDefinitions = resumeFieldDefinitionService.getFieldDefinitionsByCycleId(resume.getCycleId());

        // 查找年级字段定义（年级或大几）
        ResumeFieldDefinition gradeFieldDefinition = fieldDefinitions.stream()
                .filter(field -> "年级".equals(field.getFieldLabel()) || "大几".equals(field.getFieldLabel()))
                .findFirst()
                .orElse(null);

        // 如果找到了年级字段定义，则查找对应的字段值
        if (gradeFieldDefinition != null) {
            String grade = fieldValues.stream()
                    .filter(value -> gradeFieldDefinition.getFieldId().equals(value.getFieldId()))
                    .map(ResumeFieldValue::getFieldValue)
                    .findFirst()
                    .orElse(null);

            if (grade != null && !grade.isEmpty()) {
                return grade;
            }
        }

        // 如果简历中没有年级字段或为空，则返回空字符串
        return "";
    }
}