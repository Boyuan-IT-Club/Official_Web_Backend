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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertUsable(pdf, "简历");

        Path out = Path.of(System.getProperty("pdf.out", "target/resume-sample.pdf"));
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("PDF 已生成：" + out.toAbsolutePath() + "  " + pdf.length + " bytes");

        // 带照片再走一遍：圆形头像是裁剪路径画的，只有渲染出来才看得出
        // 有没有被拉变形、有没有盖住姓名
        byte[] withPhoto = PdfExportUtil.exportResumeToPdf(dto, portraitJpeg());
        assertUsable(withPhoto, "带头像的简历");
        assertTrue(withPhoto.length > pdf.length, "带照片的 PDF 反而更小，照片多半没画进去");
        Path out2 = Path.of(System.getProperty("pdf.photo.out", "target/resume-sample-photo.pdf"));
        Files.write(out2, withPhoto);
        System.out.println("PDF（含头像）已生成：" + out2.toAbsolutePath() + "  " + withPhoto.length + " bytes");
    }

    @Test
    void 生成一份空白报名表模板PDF() throws Exception {
        List<PdfExportUtil.TemplateField> fields = List.of(
                new PdfExportUtil.TemplateField("姓名", "input", true, List.of(), "请填写真实姓名"),
                new PdfExportUtil.TemplateField("学号", "input", true, List.of(), null),
                new PdfExportUtil.TemplateField("年级", "select", true, List.of("大一", "大二", "大三"), null),
                new PdfExportUtil.TemplateField("意愿加入部门", "checkbox", true,
                        List.of("技术部", "项目部", "媒体部", "综合部"), "最多选两个"),
                new PdfExportUtil.TemplateField("个人简介", "textarea", true, List.of(),
                        "特长、兴趣、经历，以及为什么想加入"),
                new PdfExportUtil.TemplateField("项目经验", "textarea", false, List.of(), null));

        byte[] pdf = PdfExportUtil.exportBlankTemplateToPdf("2026 秋季招新", fields);
        assertUsable(pdf, "空白模板");
        Path out = Path.of(System.getProperty("tpl.out", "target/resume-template-sample.pdf"));
        Files.createDirectories(out.getParent());
        Files.write(out, pdf);
        System.out.println("模板 PDF 已生成：" + out.toAbsolutePath() + "  " + pdf.length + " bytes");
    }

    /**
     * PDF 能不能用。
     *
     * 原来只断言「字节数 > 3000」，看着像在测内容，其实测的是「有没有嵌进图片」——
     * 早先版式在页眉放了社徽，靠那张 PNG 就过线了；换成不带图片的版式后
     * 同一份 PDF 只有 2893 字节，CI 立刻红了，而导出本身是好的。
     *
     * 真正要分清的是两件事：文件结构对不对（任何环境都该成立），
     * 以及中文字体有没有嵌进去（只有装了 CJK 字体的环境才谈得上——
     * 容器里装了 fonts-noto-cjk，CI runner 上没有）。
     */
    private static void assertUsable(byte[] pdf, String what) {
        assertTrue(pdf.length > 800, what + " PDF 过小，可能是空文档：" + pdf.length);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII),
                what + " 输出的不是 PDF");
        assertTrue(PdfExportUtil.isChineseFontAvailable(),
                what + " 找不到任何中文字体，导出的中文会是空白");
        if (PdfExportUtil.isChineseFontEmbedded()) {
            // 走嵌入这条路时，光字体子集就有上百 KB；明显小于这个量级
            // 说明子集没写进去
            assertTrue(pdf.length > 20_000,
                    what + " 用的是嵌入式字体，产物却这么小，字体多半没写进去：" + pdf.length);
        } else {
            // 内置 STSong-Light：不嵌入，整份 PDF 不到 3 KB，中文照样正常显示。
            // CI runner 走的就是这条路——第一版断言拿大小当判据，把好的导出判成了坏的
            System.out.println("【注意】" + what + " 用的是内置非嵌入字体（本机没有字体文件），"
                    + "产物偏小属正常；生产镜像装了 fonts-noto-cjk，走嵌入");
        }
    }

    /** 造一张竖构图的假证件照：验证圆形裁剪是「铺满后裁」而不是把人脸压扁 */
    private static byte[] portraitJpeg() throws Exception {
        int w = 240, h = 320;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(226, 232, 240));
        g.fillRect(0, 0, w, h);
        g.setColor(new java.awt.Color(120, 144, 180));
        g.fillOval(w / 2 - 46, 54, 92, 92);              // 头
        g.fillRoundRect(w / 2 - 74, 168, 148, 150, 40, 40); // 肩
        g.dispose();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }
}
