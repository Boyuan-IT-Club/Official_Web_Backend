package club.boyuan.official.domain.user.service;

public interface IVerificationCodeService {
    boolean verifyEmailCode(String email, String code);
    boolean verifyPhoneCode(String phone, String code);
}