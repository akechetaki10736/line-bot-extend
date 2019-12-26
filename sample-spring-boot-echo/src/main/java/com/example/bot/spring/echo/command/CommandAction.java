package com.example.bot.spring.echo.command;

import com.example.bot.spring.echo.service.Oauth2Service;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class CommandAction {
    public enum CommandEnum {
        DROPBOX_LOGIN {
            @Override
            public Message makeReplyMessage(String userId) throws Exception {
                return new TextMessage(oauth2Service.getLoginURI(userId));
            }
        }, DROPBOX_FILELIST {
            @Override
            public Message makeReplyMessage(String userId) throws Exception {
                //dpxapi
                return new TextMessage("施工中");
            }
        }, UNKNOWN_COMMAND {
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
    private static Oauth2Service oauth2Service;
    @Autowired
    CommandAction(Oauth2Service service) {
        oauth2Service = service;
    }
    public static Map<String, CommandEnum> commandMap = new HashMap<>();
}
