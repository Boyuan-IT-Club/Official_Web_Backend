//package club.boyuan.official.util;
//
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//
///**
// * 安全工具类，用于密码加密等安全相关操作
// */
//public class SecurityUtil {
//    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
//
//    /**
//     * 对密码进行BCrypt加密
//     * @param rawPassword 原始密码
//     * @return 加密后的密码
//     */
//    public static String encodePassword(String rawPassword) {
//        if (rawPassword == null) {
//            throw new IllegalArgumentException("原始密码不能为空");
//        }
//        return passwordEncoder.encode(rawPassword);
//    }
//
//    /**
//     * 验证密码是否匹配
//     * @param rawPassword 原始密码
//     * @param encodedPassword 加密后的密码
//     * @return 是否匹配
//     */
//    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
//        if (rawPassword == null || encodedPassword == null) {
//            return false;
//        }
//        return passwordEncoder.matches(rawPassword, encodedPassword);
//    }
//}