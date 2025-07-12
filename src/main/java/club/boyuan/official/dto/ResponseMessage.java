package club.boyuan.official.dto;

import org.springframework.http.HttpStatus;

public class ResponseMessage<T> {
    private Integer code;
    private String message;
    private T data;

    public ResponseMessage(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseMessage success(T data) {
        return new ResponseMessage(HttpStatus.OK.value(), "success", data);
    }

    public static <T> ResponseMessage success() {
        return new ResponseMessage(HttpStatus.OK.value(), "success", null);
    }

    public static <T> ResponseMessage error(int code, String message) {
        return new ResponseMessage<>(code, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String toString() {
        return "ResponseMessage{code = " + code + ", message = " + message + ", data = " + data + "}";
    }
}