package com.insurance.platform.chat.vo;

/** 面向公共 API 的可追溯来源视图。 */
public record ChatSource(
        String documentName,
        Integer page,
        String snippet,
        Double score) {
}
