package com.lenovo.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CmdbApplicationFromSplunk {

    private String ApplicationID;

    private String ApplicationName;

    private String ApplicationITOwner;

    private String ApplicationOwnerDomain;

    private String ApplicationOwnerTower;

    private String Description;
}
