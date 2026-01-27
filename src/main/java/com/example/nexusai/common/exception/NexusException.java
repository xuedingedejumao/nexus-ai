package com.example.nexusai.common.exception;

import lombok.Getter;

@Getter
public class NexusException extends RuntimeException {
    private final Integer code;

    public NexusException(String message) {
        super(message);
        this.code = 500;
    }

    public NexusException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
