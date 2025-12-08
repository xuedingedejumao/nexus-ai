package com.example.nexusai.enums;

import lombok.Getter;

@Getter
public enum ModelType {
    NORMAL("normal", "普通对话模型", "适合一般问答，相应速度快"),
    REASONING("reasoning", "深度思考模型", "适合复杂问题，响应速度较慢");

    private final String code;
    private final String name;
    private final String description;

    ModelType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public static ModelType fromCode(String code) {
        for (ModelType type : ModelType.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        return NORMAL;
    }
}
