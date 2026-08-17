package club.boyuan.official;

import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.SimpleResumeFieldDTO;
import club.boyuan.official.common.utils.PdfExportUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简历 PDF 模板的冒烟测试：真的生成一份并落到磁盘，人工看一眼版式。
 *
 * 光靠编译通过不足以判断 PDF 对不对——iText 的表格列宽、页脚坐标这类问题
 * 只有渲染出来才会暴露（比如末行单元格被拉宽、页脚画到页面外）。
 */
class PdfTemplateSmokeTest {

    private static SimpleResumeFieldDTO f(String key, String label, String value) {
        SimpleResumeFieldDTO d = new SimpleResumeFieldDTO();
        d.setFieldKey(key);
        d.setFieldLabel(label);
        d.setFieldValue(value);
        return d;
    }

    @Test
    void 生成一份样例简历PDF() throws Exception {
        List<SimpleResumeFieldDTO> fields = new ArrayList<>();
        fields.add(f("name", "姓名", "李明"));
        fields.add(f("student_id", "学号", "10245101480"));
        fields.add(f("email", "邮箱", "liming@stu.ecnu.edu.cn"));
        fields.add(f("phone", "手机", "13800000000"));
        fields.add(f("grade", "年级", "大二"));
        fields.add(f("gender", "性别", "男"));
        fields.add(f("major", "专业", "计算机科学与技术"));
        fields.add(f("github", "GitHub", "github.com/liming"));
        fields.add(f("expected_departments", "期望部门", "[\"技术部\",\"项目部\"]"));
        fields.add(f("tech_stack", "技术栈", "Java,Spring Boot,React,TypeScript,MySQL,Redis,Docker"));
        fields.add(f("self_introduction", "个人简介",
                "热爱后端开发，平时喜欢折腾服务器和自动化工具。大一参与过校内两个小项目，"
                        + "负责接口设计与数据库建模，也帮同学搭过 CI。"));
        fields.add(f("project_experience", "项目经验",
                "校园二手交易平台：负责后端全部接口与数据库设计，日活约 300，用 Redis 做了热点商品缓存，"
                        + "把列表页 P95 从 800ms 降到 120ms。\n"
                        + "课程管理小程序：独立完成，用 Spring Boot + 微信小程序，覆盖选课与成绩查询。\n"
                        + "自动化部署脚本：给社团服务器写了一套滚动部署脚本，把发布时间从十几分钟压到一分钟内。"));
        fields.add(f("reason", "加入原因", "想找一群能一起把项目真正做上线的人，而不是停在课程作业。"));

        ResumeDTO dto = new ResumeDTO();
        dto.setResumeId(1);
        dto.setUserId(2);
        dto.setCycleId(2);
        dto.setStatus(2);
        dto.setSubmittedAt(LocalDateTime.now().minusDays(1));
        dto.setSimpleFields(fields);

        byte[] pdf = PdfExportUtil.exportResumeToPdf(dto);
        assertTrue(pdf.length > 3000, "PDF 明显过小，可能只画出了空白页：" + pdf.length);

        Path out = Path.of(System.getProperty("pdf.out", "target/resume-sample.pdf"));
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("PDF 已生成：" + out.toAbsolutePath() + "  " + pdf.length + " bytes");
    }
}
