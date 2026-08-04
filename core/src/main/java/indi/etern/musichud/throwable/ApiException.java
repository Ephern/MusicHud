package indi.etern.musichud.throwable;

import java.net.ConnectException;

public class ApiException extends RuntimeException {
    public ApiException(ConnectException e) {
        super(e);
    }

    public ApiException() {
        super();
    }

    public ApiException(String s) {
        super(s);
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (message == null || message.isBlank()) {
            return "Unable to connect to API server | 无法连接到 API 服务器";
        } else {
            return message;
        }
    }
}
