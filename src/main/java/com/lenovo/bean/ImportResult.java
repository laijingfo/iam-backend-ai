package com.lenovo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImportResult {
    private int successCount;
    private int errorCount;
    private int ignoreCount;
    private List<String> errors;

    public ImportResult(Integer successCount, Integer errorCount, List<String> errors) {
    	this.successCount = successCount;
    	this.errorCount = errorCount;
    	this.errors = errors;
    }

    public ImportResult(Integer successCount, Integer errorCount, List<String> errors, Integer ignoreCount) {
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.errors = errors;
        this.ignoreCount = ignoreCount;
    }
}