package com.lenovo.security.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@JsonIgnoreProperties(ignoreUnknown = true)
public class JwtUserDto implements UserDetails {

    private UserLoginDto user;

    private List<Long> dataScopes;

    private List<AuthorityDto> authorities;

    @JsonCreator
    public JwtUserDto(@JsonProperty("user") UserLoginDto user,
                      @JsonProperty("dataScopes") List<Long> dataScopes,
                      @JsonProperty("authorities") List<AuthorityDto> authorities) {
        this.user = user;
        this.dataScopes = dataScopes;
        this.authorities = authorities;
    }

    @JsonIgnore
    public Set<String> getRoles() {
        return authorities.stream().map(AuthorityDto::getAuthority).collect(Collectors.toSet());
    }


    @JsonIgnore
    public String getDateRange() {
        if (Arrays.stream(new String[]{"admin", "whitelist"}).anyMatch(getRoles()::contains)) {
            return "all";
        } else {
            return user.getDateRange();
        }
    }

    @JsonIgnore
    public String getEmail() {
        return user.getMail();
    }

    @JsonIgnore
    public String getUserType() {
        return user.getType();
    }

    public UserLoginDto getUser() {
        return user;
    }

    public List<Long> getDataScopes() {
        return dataScopes;
    }

    @Override
    public List<AuthorityDto> getAuthorities() {
        return authorities;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return user.getUserName();
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return user.getStatus();
    }
}
