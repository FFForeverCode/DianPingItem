package com.hmdp.Exception;

import com.fasterxml.jackson.databind.ser.Serializers;

public class UserNullException extends BaseException {
    public UserNullException(String message) {
        super(message);
    }
}
