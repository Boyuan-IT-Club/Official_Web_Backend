package club.boyuan.official.common.utils;

import com.itextpdf.text.pdf.BaseFont;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import club.boyuan.official.domain.resume.dto.ResumeDTO;
import club.boyuan.official.domain.resume.dto.SimpleResumeFieldDTO;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 中文字体选择的回归测试。
 *
 * 起因是用户报「期望部门」的「门」渲染异常：当时字体解析写死取 .ttc 的 0 号子字体，
 * 而容器里 NotoSansCJK-Regular.ttc 的 0 号是日文子字体（实测连「简/历/项」都没字形），
 * 且用了 NOT_EMBEDDED——不嵌入时 IDENTITY_H 写的是原字体的字形编号，
 * 阅读器一替换字体就查错表，个别汉字因此变形。
 *
 * 这里不去断言「选中了哪个文件」（各环境装的字体不同），只钉死真正要紧的两件事：
 * 选出来的字体必须有简体字形，且必须能把中文写进 PDF。
 */
class PdfExportFontTest {

    private static BaseFont resolvedFont() throws Exception {
        Method m = PdfExportUtil.class.getDeclaredMethod("getChineseBaseFont");
        m.setAccessible(true);
        return (BaseFont) m.invoke(null);
    }

    @Test
    @DisplayName("选中的中文字体必须包含简体独有字形（含用户报的「门」）")
    void chosenFontCoversSimplifiedGlyphs() throws Exception {
        BaseFont bf = resolvedFont();
        assertNotNull(bf, "没有解析出任何中文字体，PDF 里的中文会整片空白");

        for (char c : "门简历项荐部社".toCharArray()) {
            assertTrue(bf.charExists(c),
                    "字体缺少字形「" + c + "」——多半又选到了日文/繁体子字体");
        }
    }

    @Test
    @DisplayName("中文能实际写进 PDF，且宽度非零")
    void chineseTextIsMeasurable() throws Exception {
        BaseFont bf = resolvedFont();
        assertNotNull(bf);

        // 宽度为 0 意味着落到了 .notdef，页面上就是空白或方框
        assertTrue(bf.getWidthPoint("期望部门", 12f) > 0f,
                "「期望部门」宽度为 0，说明这几个字没有真实字形");
    }

    @Test
    @DisplayName("导出含「期望部门」的简历，PDF 里能查到这几个字")
    void exportedPdfContainsDepartmentText() throws Exception {
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);

        SimpleResumeFieldDTO dept = new SimpleResumeFieldDTO();
        dept.setFieldKey("expected_department");
        dept.setFieldLabel("期望部门");
        dept.setFieldValue("技术部");
        dept.setFieldType("text");
        dept.setSortOrder(1);
        dto.setSimpleFields(java.util.List.of(dept));

        byte[] pdf = PdfExportUtil.exportResumeToPdf(dto);
        assertNotNull(pdf);
        assertTrue(pdf.length > 4
                        && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F',
                "导出结果没有 PDF 文件头");

        // 抽取文本能拿回「期望部门」，说明字符编码与 ToUnicode 映射都是对的；
        // 若又退回到没有中文字形的字体，这里会拿到空白或乱码
        String text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(
                new com.itextpdf.text.pdf.PdfReader(pdf), 1);
        assertTrue(text.contains("期望部门"),
                "PDF 文本层里找不到「期望部门」，实际抽到: " + text);
    }

    @Test
    @DisplayName("多行长正文不被断行规则吞掉")
    void longBodySurvivesLineBreaking() throws Exception {
        // 中文断行规则（行首禁则）曾经把正文整段截掉：IDENTITY_H 下传给
        // isSplitCharacter 的 char[] 装的是字形编号不是 Unicode，
        // 自己直接读就会在错误的位置判断能否断行，第二行整行消失。
        String line1 = "大一下开始接触前端，做过学院迎新的报名页，从零搭到上线跑了两周。";
        String line2 = "后来补了点后端，能独立写完一个带鉴权的增删改查。习惯把踩过的坑记成笔记。";

        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);

        SimpleResumeFieldDTO intro = new SimpleResumeFieldDTO();
        intro.setFieldKey("self_introduction");
        intro.setFieldLabel("自我介绍");
        intro.setFieldValue(line1 + "\n" + line2);
        intro.setFieldType("textarea");
        intro.setSortOrder(1);
        dto.setSimpleFields(java.util.List.of(intro));

        String text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(
                new com.itextpdf.text.pdf.PdfReader(PdfExportUtil.exportResumeToPdf(dto)), 1);
        String flat = text.replaceAll("\\s+", "");

        assertTrue(flat.contains(line1.replaceAll("\\s+", "")), "第一行正文丢失");
        assertTrue(flat.contains(line2.replaceAll("\\s+", "")), "第二行正文丢失——断行规则又把内容吃掉了");
    }

    @Test
    @DisplayName("PDF 用管理员配置的标签，而不是写死的中文名")
    void usesAdminConfiguredLabels() throws Exception {
        // 管理员把「GitHub主页」改成「代码仓库」后，表单跟着变而 PDF 不变，
        // 同一份简历「表里填的」和「导出的」就对不上了
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);

        SimpleResumeFieldDTO gh = new SimpleResumeFieldDTO();
        gh.setFieldKey("github");
        gh.setFieldLabel("代码仓库");
        gh.setFieldValue("github.com/zhangsan");
        gh.setFieldType("text");
        gh.setSortOrder(1);
        dto.setSimpleFields(java.util.List.of(gh));

        String text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(
                new com.itextpdf.text.pdf.PdfReader(PdfExportUtil.exportResumeToPdf(dto)), 1);

        assertTrue(text.contains("代码仓库"), "没用上管理员改的标签，实际抽到: " + text);
        assertTrue(!text.contains("GitHub主页"), "仍在使用写死的标签");
    }

    @Test
    @DisplayName("没配标签时回落到内置中文名")
    void fallsBackToBuiltinLabel() throws Exception {
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);

        SimpleResumeFieldDTO f = new SimpleResumeFieldDTO();
        f.setFieldKey("student_id");
        f.setFieldLabel(null);            // 没有标签
        f.setFieldValue("10235101468");
        f.setFieldType("text");
        f.setSortOrder(1);
        dto.setSimpleFields(java.util.List.of(f));

        String text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(
                new com.itextpdf.text.pdf.PdfReader(PdfExportUtil.exportResumeToPdf(dto)), 1);
        assertTrue(text.contains("学号"), "没有回落到内置标签，实际抽到: " + text);
    }
}
