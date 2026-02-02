package club.boyuan.official.service.impl;

import club.boyuan.official.dto.InterviewResultResponseDTO;
import club.boyuan.official.dto.SendNotificationsRequestDTO;
import club.boyuan.official.dto.SendNotificationsResponseDTO;
import club.boyuan.official.entity.InterviewResult;
import club.boyuan.official.entity.User;
import club.boyuan.official.mapper.InterviewResultMapper;
import club.boyuan.official.service.IInterviewResultService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.MessageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
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
    public List<InterviewResultResponseDTO> list(Integer cycleId, String name, String decision, String department, Integer page, Integer size) {

        //构建查询对象
        LambdaQueryWrapper<InterviewResult> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(InterviewResult::getCycleId, cycleId)
                .like(name != null, InterviewResult::getName, name)
                .like(decision != null, InterviewResult::getDecision, decision)
                .like(department != null, InterviewResult::getAssignedDeptId, department);
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
