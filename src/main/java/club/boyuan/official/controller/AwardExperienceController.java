package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.entity.User;
import club.boyuan.official.service.IAwardExperienceService;
import club.boyuan.official.service.IUserService;
import club.boyuan.official.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/awards")
public class AwardExperienceController {

    @Autowired
    private IAwardExperienceService awardExperienceService;

       @Autowired
    private IUserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    /**
     * 创建获奖经历
     */
    @PostMapping
    public ResponseEntity<ResponseMessage> createAward(@RequestBody AwardExperience awardExperience) {
        AwardExperience createdAward = awardExperienceService.create(awardExperience);
        Map<String, Integer> data = new HashMap<>();
        data.put("award_id", createdAward.getAwardId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseMessage(201, "记录已添加", data));
    }

    /**
     * 根据ID获取获奖经历
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseMessage> getAwardById(@PathVariable Integer id) {
        AwardExperience award = awardExperienceService.getById(id);
        return ResponseEntity.ok(new ResponseMessage(200, "success", award));
    }

    /**
     * 根据用户ID获取所有获奖经历
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ResponseMessage> getAwardsByUserId(@PathVariable Integer userId) {
        List<AwardExperience> awards = awardExperienceService.getByUserId(userId);
        return ResponseEntity.ok(new ResponseMessage(200, "success", awards));
    }

    /**
     * 更新获奖经历
     */
    @PutMapping
    public ResponseEntity<ResponseMessage> updateAward(@RequestBody AwardExperience awardExperience, HttpServletRequest request) {
        // 从JWT令牌获取当前登录用户ID
        String token = request.getHeader("Authorization").substring(7);
        String username = jwtTokenUtil.extractUsername(token);
        User user = userService.getUserByUsername(username);
        Integer currentUserId = user.getUserId();
        
        // 获取要更新的获奖经历原始数据
        AwardExperience originalAward = awardExperienceService.getById(awardExperience.getAwardId());
        if (originalAward == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage(404, "获奖经历不存在", null));
        }
        
        // 验证当前用户是否为获奖经历的所有者
        if (!originalAward.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ResponseMessage(403, "没有权限修改此获奖经历", null));
        }
        
        // 设置用户ID为当前登录用户ID，防止篡改
        awardExperience.setUserId(currentUserId);
        AwardExperience updatedAward = awardExperienceService.update(awardExperience);
        return ResponseEntity.ok(new ResponseMessage(200, "记录已更新", null));
    }

    /**
     * 删除获奖经历
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseMessage> deleteAward(@PathVariable Integer id) {
        awardExperienceService.deleteById(id);
        return ResponseEntity.ok(new ResponseMessage(200, "获奖经历删除成功", null));
    }
}