package club.boyuan.official.dto;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一响应消息封装类
 * @param <T> 数据类型
 */
@Getter
@Setter
public class ResponseMessage<T> {
    /**
     * 响应状态码
     */
    private Integer code;
    
    /**
     * 响应消息
     */
    private String message;
    
    /**
     * 响应数据
     */
    private T data;

    public ResponseMessage(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功响应（带数据）
     * @param data 响应数据
     * @param <T> 数据类型
     * @return ResponseMessage实例
     */
    public static <T> ResponseMessage<T> success(T data) {
        return new ResponseMessage<>(200, "操作成功", data);
    }

    /**
     * 成功响应（无数据）
     * @param <T> 数据类型
     * @return ResponseMessage实例
     */
    public static <T> ResponseMessage<T> success() {
        return new ResponseMessage<>(200, "操作成功", null);
    }

    /**
     * 错误响应
     * @param code 错误码
     * @param message 错误信息
     * @param <T> 数据类型
     * @return ResponseMessage实例
     */
    public static <T> ResponseMessage<T> error(int code, String message) {
        return new ResponseMessage<>(code, message, null);
    }

    @Override
    public String toString() {
        return "ResponseMessage{code=" + code + ", message='" + message + "', data=" + data + "}";
    }
}