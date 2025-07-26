package club.boyuan.official.service;

public interface IVerificationCodeService {
    boolean verifyEmailCode(String email, String code);
    boolean verifyPhoneCode(String phone, String code);
}