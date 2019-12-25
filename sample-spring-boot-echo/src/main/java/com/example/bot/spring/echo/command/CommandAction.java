package com.example.bot.spring.echo.command;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxStandardSessionStore;
import com.dropbox.core.DbxWebAuth;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandAction {

    public static Map<String, CommandEnum> commandMap = new HashMap<>();
    public static DbxWebAuth webAuth;
    public enum CommandEnum {
        DROPBOX_LOGIN {
            @Override
            public Message makeReplyMessage(String userId) throws Exception {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                HttpSession httpSession = attributes.getRequest().getSession(true);
                DbxAppInfo appInfo = new DbxAppInfo("pinzzhq3gm40hae", "9sebf4s438qk0m1");
                DbxRequestConfig requestConfig = new DbxRequestConfig("line-bot-dropbox-authorize");
                webAuth = new DbxWebAuth(requestConfig, appInfo);
                DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                        .withRedirectUri("https://114de623.ngrok.io/oauth2callback", new DbxStandardSessionStore(httpSession, "pinzzhq3gm40hae"))
                        .withState("uid=" + userId)
                        .build();
                return new TextMessage("plz login: \n" + webAuth.authorize(webAuthRequest));
            }
        }, DROPBOX_FILELIST {
            @Override
            public Message makeReplyMessage(String userId) throws Exception {
                return new TextMessage("施工中");
            }
        },UNKNOWN_COMMAND{
            @Override
            public Message makeReplyMessage(String userId) throws Exception {
                return new TextMessage("跨謀");
            }
        };



        CommandEnum() {
            commandMap.put(this.name().toLowerCase(), this);
        }


        public abstract Message makeReplyMessage(String userId) throws Exception;
    }
}
