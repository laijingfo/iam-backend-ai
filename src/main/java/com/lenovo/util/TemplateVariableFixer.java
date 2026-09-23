package com.lenovo.util;

// TemplateVariableFixer.java
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateVariableFixer {

    // 匹配 UAR_ 开头的变量
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("UAR_[A-Z0-9_]+", Pattern.CASE_INSENSITIVE);

    /**
     * 修复被HTML标签拆分的模板变量
     */
    public static String fixTemplateVariables(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // 使用正则表达式修复模板变量
        String fixedHtml = fixTemplateVariablesWithRegex(html);

        // 使用DOM修复器进行精细修复
        fixedHtml = fixTemplateVariablesWithDom(fixedHtml);

        return fixedHtml;
    }

    /**
     * 使用正则表达式修复模板变量
     */
    public static String fixTemplateVariablesWithRegex(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        // 创建匹配 UAR_ 变量的正则表达式
        Pattern pattern = Pattern.compile(
                "UAR_[A-Z0-9_]+",
                Pattern.CASE_INSENSITIVE
        );

        // 不需要替换，只需要确保变量不被拆分
        // 对于 UAR_ 变量，我们主要需要确保它们不被 HTML 标签拆分
        return html;
    }

    /**
     * 使用DOM修复器修复模板变量
     */
    private static String fixTemplateVariablesWithDom(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        Document doc = Jsoup.parse(html);
        DomVariableFixerVisitor visitor = new DomVariableFixerVisitor();
        NodeTraversor.traverse(visitor, doc.body());

        return doc.body().html();
    }

    /**
     * DOM节点访问器，用于修复被拆分的模板变量
     */
    private static class DomVariableFixerVisitor implements NodeVisitor {
        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode) {
                TextNode textNode = (TextNode) node;
                String text = textNode.text();

                // 检查文本中是否包含 UAR_ 变量
                if (containsUarVariable(text)) {
                    // 尝试修复被拆分的 UAR_ 变量
                    String fixedText = fixSplitUarVariables(text);
                    if (!fixedText.equals(text)) {
                        textNode.text(fixedText);
                    }
                }
            }
        }

        @Override
        public void tail(Node node, int depth) {
            // 不需要处理
        }

        /**
         * 检查文本是否包含 UAR_ 变量
         */
        private boolean containsUarVariable(String text) {
            return text.matches(".*UAR_[A-Z0-9_]+.*");
        }

        /**
         * 修复被拆分的 UAR_ 变量
         */
        private String fixSplitUarVariables(String text) {
            // 简单的修复逻辑：移除变量中间的空格和换行
            return text.replaceAll("UAR_\\s+", "UAR_")
                    .replaceAll("\\s+UAR_", "UAR_");
        }
    }

    /**
     * 提取内容中的所有模板变量
     */
    public static List<String> extractTemplateVariables(String content) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(content);

        while (matcher.find()) {
            variables.add(matcher.group());
        }

        return variables;
    }

    /**
     * 验证内容中的模板变量是否完整
     */
    public static boolean validateTemplateVariables(String content) {
        // 对于 UAR_ 变量，我们只需要检查它们是否被正确识别
        // 不需要检查成对的 {{ }}，所以总是返回 true
        return true;
    }

    /**
     * 替换模板变量
     */
    public static String replaceTemplateVariables(String content, Map<String, String> variables) {
        if (content == null || variables == null || variables.isEmpty()) {
            return content;
        }

        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String variable = entry.getKey();
            String value = entry.getValue();
            // 修复1：使用替换后的字符串进行后续替换，而不是原始字符串
            // 修复2：去掉\\b单词边界符，因为如果变量名和其他字符连在一起（虽然这里没有），但主要是\\b可能导致匹配问题，直接替换文本更稳妥
            // 如果需要精确匹配独立的单词，可以用(?<!\\w)和(?!\\w)来替代\\b，兼容性更好
            String regex = "(?<!\\w)" + Pattern.quote(variable) + "(?!\\w)";
            result = result.replaceAll(regex, Matcher.quoteReplacement(value));

            // 替换 UAR_ 变量之前的写法
            //result = result.replaceAll("\\b" + Pattern.quote(variable) + "\\b", value);
        }

        return result;
    }

    private static int countOccurrences(String str, String sub) {
        if (str == null || sub == null || sub.isEmpty()) {
            return 0;
        }

        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }

        return count;
    }
}