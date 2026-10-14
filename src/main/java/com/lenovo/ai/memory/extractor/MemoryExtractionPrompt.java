package com.lenovo.ai.memory.extractor;

import com.lenovo.ai.memory.model.MemoryType;

import java.util.Set;

/**
 * 长期记忆抽取的 Prompt 模板与类型白名单。
 * <p>纯常量/静态方法，无 Spring 依赖，便于单测。</p>
 */
public final class MemoryExtractionPrompt {

    /** 允许的记忆类型（由 MemoryType 统一维护） */
    public static final Set<String> ALLOWED_TYPES = MemoryType.ALLOWED;

    private MemoryExtractionPrompt() {}

    public static String build(String dialog) {
        return """
            从以下对话中抽取值得长期记住的**用户信息**。
            只输出 JSON 数组，不要任何解释。格式：
            [{"type":"PREFERENCE|FACT|ENTITY|QUESTION","content":"...","importance":0.0~1.0}]
            没有值得记住的信息则输出 []。
            
            ========== 类型定义 ==========
            - PREFERENCE：用户偏好（语言、风格、习惯、喜好）
              例："用户偏好中文回答"、"用户喜欢简洁风格"
            
            - FACT：关于用户的**稳定事实**，只要用户明确陈述了自己的
              身份、职位、部门、公司、角色、职责、工作内容、联系方式等，
              **必须**抽为 FACT。
            
            - ENTITY：用户关注的具体对象（项目名、系统名、人名、产品名）
              例："我在做智慧园区项目"  → {"type":"ENTITY","content":"用户关注智慧园区项目"}
            
            - QUESTION：用户询问或持续关注的业务主题
            
            ========== 判断规则 ==========
            1. **身份陈述必须抽**：只要用户说"我是/我叫/我在/我负责/我的职位是"等，
               一律抽为 FACT，importance ≥ 0.8。
            2. **不要漏掉任何有信息量的内容**：宁可多抽一条，也不要漏。
            3. **不抽的只有**：纯寒暄（"你好""谢谢""好的""嗯"）、无信息量的确认。
            4. **不要把助手的回答当作记忆**，只记用户说的。
            5. **一条对话可以抽多条**：例如用户说"我是laijf2财务经理，想查审核流程"
               → 抽 2 条：FACT（用户是财务经理）+ QUESTION（用户关注审核流程）。
            
            ========== 对话 ==========
            %s
            """.formatted(dialog);
    }
}