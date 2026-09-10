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
    @DisplayName("管理员改过的小节标题要出现在 PDF 里")
    void usesAdminConfiguredLabels() throws Exception {
        // 管理员把「项目经验」改成「作品集」后，表单跟着变而 PDF 不变，
        // 同一份简历「表里填的」和「导出的」就对不上了
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);

        SimpleResumeFieldDTO f = new SimpleResumeFieldDTO();
        f.setFieldKey("project_experience");
        f.setFieldLabel("作品集");
        f.setFieldValue("给社团写过一套部署脚本");
        f.setFieldType("textarea");
        f.setSortOrder(1);
        dto.setSimpleFields(java.util.List.of(f));

        String text = extractText(dto);

        assertTrue(text.contains("作品集"), "没用上管理员改的标签，实际抽到: " + text);
        assertTrue(!text.contains("项目经验"), "仍在使用写死的标签");
    }

    @Test
    @DisplayName("没配标签时回落到内置中文名")
    void fallsBackToBuiltinLabel() throws Exception {
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);

        SimpleResumeFieldDTO f = new SimpleResumeFieldDTO();
        f.setFieldKey("self_introduction");
        f.setFieldLabel(null);            // 没有标签
        f.setFieldValue("热爱后端开发");
        f.setFieldType("textarea");
        f.setSortOrder(1);
        dto.setSimpleFields(java.util.List.of(f));

        assertTrue(extractText(dto).contains("自我介绍"),
                "没有回落到内置标签，实际抽到: " + extractText(dto));
    }

    /**
     * 身份行里的字段只出值、不出标签。
     *
     * 经典单栏版式把学号、专业、手机、邮箱、GitHub 压成抬头下的两行小字，
     * 那里加标签只会变吵，而值本身已经自明（一串数字、一个邮箱、一个仓库地址）。
     * 所以这些字段不再有「标签对不对得上」的问题——但值必须在。
     */
    @Test
    @DisplayName("学号与联系方式出现在抬头的身份行里")
    void identityLineCarriesValues() throws Exception {
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setUserName("张三");
        dto.setStatus(2);
        dto.setSimpleFields(java.util.List.of(
                field("name", "姓名", "张三"),
                field("student_id", "学号", "10235101468"),
                field("github", "代码仓库", "github.com/zhangsan")));

        String text = extractText(dto);
        assertTrue(text.contains("10235101468"), "学号没出现在 PDF 里，实际抽到: " + text);
        assertTrue(text.contains("github.com/zhangsan"), "GitHub 没出现在 PDF 里，实际抽到: " + text);
    }

    @Test
    @DisplayName("表单里不渲染的字段不进导出：个人简介与自我介绍重复，早已从表单撤下")
    void skipsNonFormFields() throws Exception {
        ResumeDTO dto = new ResumeDTO();
        dto.setUserId(1);
        dto.setStatus(2);
        dto.setSimpleFields(java.util.List.of(
                field("self_introduction", "自我介绍", "热爱后端开发"),
                // 字段定义表里它还 is_active=1（历史周期建的），但表单不渲染。
                // 不滤的话导出会多出一个学生自己都填不了的空栏（用户实测反馈）
                field("introduction", "个人简介", "这段不该出现在导出里"),
                field("expected_departments", "期望部门", "[\"技术部\"]")));

        String text = extractText(dto);
        assertTrue(text.contains("自我介绍"), "正常字段被误滤了，实际抽到: " + text);
        assertTrue(!text.contains("个人简介"), "表单不渲染的字段仍进了导出: " + text);
        assertTrue(!text.contains("期望部门"), "期望部门由第一/第二志愿合成，不该单独成栏: " + text);
    }

    private static SimpleResumeFieldDTO field(String key, String label, String value) {
        SimpleResumeFieldDTO f = new SimpleResumeFieldDTO();
        f.setFieldKey(key);
        f.setFieldLabel(label);
        f.setFieldValue(value);
        f.setFieldType("text");
        f.setSortOrder(1);
        return f;
    }

    private static String extractText(ResumeDTO dto) throws Exception {
        return com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(
                new com.itextpdf.text.pdf.PdfReader(PdfExportUtil.exportResumeToPdf(dto)), 1);
    }
}
