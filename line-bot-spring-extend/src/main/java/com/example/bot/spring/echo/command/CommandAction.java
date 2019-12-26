package com.example.bot.spring.echo.command;

import com.example.bot.spring.echo.service.DropboxServiceImpl;
import com.example.bot.spring.echo.service.Oauth2Service;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
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
                DropboxServiceImpl imp = (DropboxServiceImpl) oauth2Service;
                List<String> browseList;
                if(imp.getFilesList(userId).isPresent())
                    browseList = imp.getFilesList(userId).get();
                else
                    return new TextMessage("Plz login first");
                return new TextMessage(browseList.get(0) + browseList.get(1));
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
