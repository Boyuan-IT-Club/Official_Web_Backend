package club.boyuan.official.dto;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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

    public String toString() {
        return "ResponseMessage{code = " + code + ", message = " + message + ", data = " + data + "}";
    }
}