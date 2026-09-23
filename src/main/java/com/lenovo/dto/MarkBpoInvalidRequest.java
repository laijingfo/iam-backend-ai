package com.lenovo.dto;

import lombok.Data;

import java.util.List;

@Data
public class MarkBpoInvalidRequest {
    private String cmdbId;
    private List<String> systemRoles;
    private String bpo;
}
