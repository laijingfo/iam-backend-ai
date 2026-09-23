package com.lenovo.bean;

import lombok.Data;

@Data
public class AdvancedSearchBean {

    private String label;
    private String value;

    public AdvancedSearchBean(String label, String value) {
        this.label = label;
        this.value = value;
    }
}
