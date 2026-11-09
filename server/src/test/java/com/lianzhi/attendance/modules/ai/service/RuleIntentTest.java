package com.lianzhi.attendance.modules.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 考勤规则关键词识别测试
 */
class RuleIntentTest {

    private static final String[] RULE_KEYWORDS = {
            "几类", "分为", "规定", "规则", "怎么算", "怎么界定",
            "处罚", "豁免", "有效期", "流程", "手续", "条件", "标准", "定义"
    };

    private boolean isRuleIntent(String q) {
        if (q == null || q.isBlank()) return false;
        for (String kw : RULE_KEYWORDS) {
            if (q.contains(kw)) return true;
        }
        return false;
    }

    @Test
    @DisplayName("规则类问句应被识别")
    void ruleQuestions_detected() {
        assertTrue(isRuleIntent("请假的假期分为哪几类"));     // "分为" / "几类"
        assertTrue(isRuleIntent("公司考勤规定是什么"));       // "规定"
        assertTrue(isRuleIntent("年假有效期多久"));           // "有效期"
        assertTrue(isRuleIntent("漏打卡怎么补手续"));         // "手续"
    }

    @Test
    @DisplayName("空/null 安全返回 false")
    void blank_input() {
        assertFalse(isRuleIntent(null));
        assertFalse(isRuleIntent(""));
        assertFalse(isRuleIntent("   "));
    }
}
