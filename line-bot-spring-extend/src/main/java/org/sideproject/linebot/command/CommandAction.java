package org.sideproject.linebot.command;

import org.sideproject.linebot.service.DropboxServiceImpl;
import org.sideproject.linebot.service.Oauth2Service;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CommandAction {
    public enum CommandEnum {
        LOGIN {
            @Override
            public Message makeReplyMessage(List<String> context) throws Exception {

                String userId = context.get(0);
                return new TextMessage(oauth2Service.getLoginURI(userId));
            }
        },
        BROWSE {
            @Override
            public Message makeReplyMessage(List<String> context) throws Exception {
                //dpxapi
                String userId = context.get(0);
                DropboxServiceImpl imp = (DropboxServiceImpl) oauth2Service;
                List<String> browseList;
                if(imp.getFilesList(userId).isPresent())
                    browseList = imp.getFilesList(userId).get();
                else
                    return new TextMessage("Plz login first");
                return new TextMessage(browseList.get(0) + browseList.get(1));
            }
        },
        FW {
            @Override
            public Message makeReplyMessage(List<String> context) throws Exception {
                return null;
            }
        },
        BACK {
            @Override
            public Message makeReplyMessage(List<String> context) throws Exception {
                return null;
            }
        },
        DL{
            @Override
            public Message makeReplyMessage(List<String> context) throws Exception {
                return null;
            }
        },
        UNKNOWN_COMMAND {
            @Override
            public Message makeReplyMessage(List<String> context) throws Exception {
                return new TextMessage("跨謀");
            }
        };
        CommandEnum() {
            commandMap.put("!" + this.name().toLowerCase(), this);
        }
        public abstract Message makeReplyMessage(List<String> context) throws Exception;
    }
    private static Oauth2Service oauth2Service;
    @Autowired
    CommandAction(Oauth2Service service) {
        oauth2Service = service;
    }
    public static Map<String, CommandEnum> commandMap = new HashMap<>();
}
