package club.boyuan.official.service;

public interface VerificationCodeService {
    boolean verifyEmailCode(String email, String code);
    boolean verifyPhoneCode(String phone, String code);
    void sendEmailCode(String email);
    void sendSmsCode(String phone);
}