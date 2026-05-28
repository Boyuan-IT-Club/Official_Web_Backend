package club.boyuan.official.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 投递到 RabbitMQ 的邮箱验证码消息体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String code;
}
