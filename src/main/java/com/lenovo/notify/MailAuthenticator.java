package com.lenovo.notify;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;

/**
 * @author : chenhao
 * @date : 2023/2/20
 * @description :
 */
public class MailAuthenticator extends Authenticator {
    /**
     * 用户名
     */
    private String userName;
    /**
     * 密码
     */
    private String userPass;

    public MailAuthenticator() {
    }

    public MailAuthenticator(String userName, String userPass) {
        this.userName = userName;
        this.userPass = userPass;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(userName, userPass);
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPass() {
        return userPass;
    }

    public void setUserPass(String userPass) {
        this.userPass = userPass;
    }

}
