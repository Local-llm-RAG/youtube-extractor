package com.youtube.gpt;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum GPTTaskPriceMultiplier {

    SHORT_ANSWER("0.1"),
    SUMMARY("0.2"),
    UNKNOWN("0.3"),
    EXPLANATION_QA("0.4"),
    EXPANSION("0.5"),
    DEEP_DIVE("0.6");

    private final BigDecimal value;

    GPTTaskPriceMultiplier(String value) {
        this.value = new BigDecimal(value);
    }
}
