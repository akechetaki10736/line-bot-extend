package org.sideproject.linebot.service;

public interface Oauth2Service {
    String getLoginURI(String lineUserId);
    void finishOauth(final String code, final String state) throws Exception;

}
