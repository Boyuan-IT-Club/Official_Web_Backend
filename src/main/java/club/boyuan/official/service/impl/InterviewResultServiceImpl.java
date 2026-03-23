package club.boyuan.official.service.impl;

import club.boyuan.official.dto.InterviewResultResponseDTO;
import club.boyuan.official.dto.InterviewResultSaveDTO;
import club.boyuan.official.dto.SendNotificationsRequestDTO;
import club.boyuan.official.dto.SendNotificationsResponseDTO;
import club.boyuan.official.entity.InterviewResult;
import club.boyuan.official.entity.User;
import club.boyuan.official.mapper.InterviewResultMapper;
import club.boyuan.official.service.IInterviewResultService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.MessageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * <p>
 * 面试结果表 服务实现类
 * </p>
 *
 * @author dhy
 * @since 2026-01-28
 */
@Service
@Slf4j
public class InterviewResultServiceImpl extends ServiceImpl<InterviewResultMapper, InterviewResult> implements IInterviewResultService {

    @Autowired
    private IUserService userService;
    @Autowired
    private MessageUtils messageUtils;
    @Override
    public SendNotificationsResponseDTO sendNotifications(SendNotificationsRequestDTO requestDTO) {
        List<Integer> resultIds = requestDTO.getResultIds();
        //发送通知的类型（暂时只有邮箱，未开通sms短信服务）
        String notificationType = requestDTO.getNotificationType();
        String customMessage = requestDTO.getCustomMessage();

        int sendCount=0;
        int failedCount=0;
        ArrayList<Integer> failedId = new ArrayList<>();

        for(Integer resultId:resultIds){
            try {
                //根据resultId查询面试结果
                InterviewResult interviewResult = this.getById(resultId);
                if(interviewResult==null){
                    failedCount++;
                    failedId.add(-1);
                    throw new RuntimeException("面试结果不存在");
                }
                //获取用户信息
                User user = userService.getById(interviewResult.getUserId());
                if(user==null){
                    failedCount++;
                    failedId.add(-1);
                    throw new RuntimeException("用户不存在");
                }
                //根据通知类型发送通知
                Boolean sent = false;
                switch (notificationType.toLowerCase()){
                    case "email":
                        sent = sendEmailNotifaction(user,interviewResult,customMessage);
                        break;
                    case "sms":
                        sent = sendSmsNotifaction(user,interviewResult,customMessage);
                        break;
                    default:
                        throw new RuntimeException("不支持的通知类型");
                }
                if(sent){
                    sendCount++;
                }else{
                    failedCount++;
                }
            }catch (Exception e){
                log.error("发送通知失败，resultId: {}", resultId, e);
                failedCount++;
                failedId.add(resultId);
            }
        }
        SendNotificationsResponseDTO responseDTO = new SendNotificationsResponseDTO();
        responseDTO.setSentCount(sendCount);
        responseDTO.setFailedCount(failedCount);
        responseDTO.setFailedId(failedId);
        return responseDTO;

    }

    @Override
    public InterviewResultResponseDTO list(Integer cycleId, String name, String decision, String department, Integer page, Integer size) {

        //构建查询对象
        Page<InterviewResult> pageInfo = new Page<>(page, size);
        Page<InterviewResult> resultPage = baseMapper.selectResultPage(pageInfo, cycleId, name, decision, department);
        InterviewResultResponseDTO responseDTO = new InterviewResultResponseDTO();
        responseDTO.setTotal(resultPage.getTotal());
        responseDTO.setInterviewResults(resultPage.getRecords());
        return responseDTO;

    }

    @Override
    public InterviewResult update(Integer resultId, InterviewResultSaveDTO interviewResult) {
        //从 Spring Security 获取userId
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Integer userId = null;
        if (authentication != null) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof String) {
                // 如果是用户名，需要查询用户ID
                String username = (String) principal;
                User user = userService.getUserByUsername(username);
                userId = user != null ? user.getUserId() : null;
            } else if (principal instanceof UserDetails) {
                // 如果是UserDetails，获取用户名再查询
                String username = ((UserDetails) principal).getUsername();
                User user = userService.getUserByUsername(username);
                userId = user != null ? user.getUserId() : null;
            }
        }
        if (userId != null) {
            interviewResult.setDecisionBy(userId);
        }

        //构建更新对象
        LambdaUpdateWrapper<InterviewResult> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper
                .eq(InterviewResult::getResultId, resultId)
                .set(interviewResult.getDecision()!=null, InterviewResult::getDecision, interviewResult.getDecision())
                .set(interviewResult.getAssignedDeptId()!=null, InterviewResult::getAssignedDeptId, interviewResult.getAssignedDeptId())
                .set(InterviewResult::getDecisionBy, interviewResult.getDecisionBy());
        this.update(updateWrapper);
        return this.getById(resultId);

    }

    //sms尚未开通，短信通知功能留白
    private Boolean sendSmsNotifaction(User user, InterviewResult interviewResult, String customMessage) {
        return false;
    }

    private Boolean sendEmailNotifaction(User user, InterviewResult interviewResult, String customMessage) {
        try{
            String email = user.getEmail();
            //判断邮箱状态
            if(email == null || email.isEmpty()){
                log.warn("用户{}的邮箱为空，无法发送邮件", user.getUsername());
                return false;
            }
            //校验邮箱格式
            messageUtils.validateEmail(email);
            messageUtils.sendEmail(email, "博远信息技术社面试结果通知", customMessage);
            log.info("用户{}的邮箱发送成功", user.getUsername());
            return true;
        }catch (Exception e){
            log.error("用户{}的邮箱发送失败", user.getUsername(), e);
            return false;
        }
    }
}
