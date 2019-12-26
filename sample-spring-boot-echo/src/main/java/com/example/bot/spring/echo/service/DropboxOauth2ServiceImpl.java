package com.example.bot.spring.echo.service;
import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Service
public class DropboxOauth2ServiceImpl implements Oauth2Service{
    private final Logger log = LoggerFactory.getLogger(DropboxOauth2ServiceImpl.class);
    private Map<String, DbxWebAuth> dpxAuthMap;
    private Map<String, DbxAuthFinish> dpxAccessMap;
    private DbxAppInfo appInfo;
    private DbxRequestConfig requestConfig;

    public DropboxOauth2ServiceImpl( ) {
        this.dpxAuthMap =  new HashMap<>();
        this.dpxAccessMap = new HashMap<>();
        this.appInfo = new DbxAppInfo("pinzzhq3gm40hae", "9sebf4s438qk0m1");
        this.requestConfig = new DbxRequestConfig("line-bot-dropbox-authorize");
    }

    @Override
    public String getLoginURI(String lineUserId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpSession httpSession = attributes.getRequest().getSession(true);
        DbxWebAuth webAuth = new DbxWebAuth(requestConfig, appInfo);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withRedirectUri("https://42ce300e.ngrok.io/oauth2callback", new DbxStandardSessionStore(httpSession, "pinzzhq3gm40hae"))
                .withState("uid=" + lineUserId)
                .build();
        dpxAuthMap.put(lineUserId, webAuth);
        return webAuth.authorize(webAuthRequest);
    }
    @Override
    public void finishOauth(final String code, final String state) {
        DbxAuthFinish authFinish;
        String userId = state.substring(state.indexOf("uid=")+4);
        DbxWebAuth dbxWebAuth = dpxAuthMap.get(userId);
        if(dbxWebAuth == null) {
            log.error("Can not find webAuth with userId : {}", userId);
            return;
        }
        try {
            authFinish = dbxWebAuth.finishFromCode(code, "https://42ce300e.ngrok.io/oauth2callback");
        } catch (DbxException ex) {
            System.err.println("Error in DbxWebAuth.authorize: " + ex.getMessage());
            return;
        }
        dpxAuthMap.remove(userId);
        dpxAccessMap.put(userId, authFinish);
        log.info("Authorization complete.");
        log.info("- User ID: " + authFinish.getUserId());
        log.info("- State: " + userId);
        log.info("- Account ID: " + authFinish.getAccountId());
        log.info("- Access Token: " + authFinish.getAccessToken());
    }
}
