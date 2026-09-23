package com.lenovo.bean;

import lombok.Data;

import java.util.List;

@Data
public class PreSendBean {
    private Long id;
    private String to;
    private String toEmail;
    private String cc;
    private String ccEmail;

}
