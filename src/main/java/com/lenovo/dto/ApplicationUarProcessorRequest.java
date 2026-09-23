package com.lenovo.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApplicationUarProcessorRequest {
    private String cmdbId;
    private List<String> itCode;
    private String email;
}
