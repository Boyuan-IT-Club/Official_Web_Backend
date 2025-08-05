package club.boyuan.official.utils;

import club.boyuan.official.exception.BusinessException;
import club.boyuan.official.exception.BusinessExceptionEnum;

import java.util.regex.Pattern;

/**
 * 密码验证工具类
 * 
 * 密码复杂度要求：
 * 1. 长度至少8位
 * 2. 包含小写字母、大写字母、数字、特殊字符中的至少三种
 */
public class PasswordValidator {

    // 密码长度至少8位
    private static final int MIN_LENGTH = 8;
    
    // 包含小写字母的正则表达式
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    
    // 包含大写字母的正则表达式
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    
    // 包含数字的正则表达式
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    
    // 包含特殊字符的正则表达式
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

    /**
     * 验证密码复杂度
     * 密码必须满足以下条件：
     * 1. 长度至少8位
     * 2. 包含小写字母、大写字母、数字、特殊字符中的至少三种
     * 
     * @param password 待验证的密码
     * @throws BusinessException 当密码不符合要求时抛出异常
     */
    public static void validate(String password) {
        // 检查密码是否为空
        if (password == null) {
            throw new BusinessException(BusinessExceptionEnum.PASSWORD_TOO_SIMPLE, "密码不能为空");
        }
        
        // 检查密码长度
        if (password.length() < MIN_LENGTH) {
            throw new BusinessException(BusinessExceptionEnum.PASSWORD_TOO_SIMPLE, "密码长度不能少于8位");
        }

        int complexityCount = 0;
        
        // 检查是否包含小写字母
        if (LOWERCASE_PATTERN.matcher(password).matches()) {
            complexityCount++;
        }
        
        // 检查是否包含大写字母
        if (UPPERCASE_PATTERN.matcher(password).matches()) {
            complexityCount++;
        }
        
        // 检查是否包含数字
        if (DIGIT_PATTERN.matcher(password).matches()) {
            complexityCount++;
        }
        
        // 检查是否包含特殊字符
        if (SPECIAL_CHAR_PATTERN.matcher(password).matches()) {
            complexityCount++;
        }
        
        // 必须包含至少三种类型的字符
        if (complexityCount < 3) {
            throw new BusinessException(BusinessExceptionEnum.PASSWORD_TOO_SIMPLE, "密码必须包含小写字母、大写字母、数字、特殊字符中的至少三种");
        }
    }
}