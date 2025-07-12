package club.boyuan.official.controller;

import club.boyuan.official.dto.ResponseMessage;
import club.boyuan.official.entity.AwardExperience;
import club.boyuan.official.service.IAwardExperienceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/awards")
public class AwardExperienceController {

    @Autowired
    private IAwardExperienceService awardExperienceService;

    /**
     * 创建获奖经历
     */
    @PostMapping
    public ResponseEntity<ResponseMessage> createAward(@RequestBody AwardExperience awardExperience) {
        AwardExperience createdAward = awardExperienceService.create(awardExperience);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ResponseMessage(201, "获奖经历创建成功", createdAward));
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
    public ResponseEntity<ResponseMessage> updateAward(@RequestBody AwardExperience awardExperience) {
        AwardExperience updatedAward = awardExperienceService.update(awardExperience);
        return ResponseEntity.ok(new ResponseMessage(200, "获奖经历更新成功", updatedAward));
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