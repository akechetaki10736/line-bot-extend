package com.example.bot.spring.echo.controller;


import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxException;
import com.example.bot.spring.echo.command.CommandAction;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.singletonList;

@Slf4j
@LineMessageHandler
@RestController
public class CommandController {

    @Autowired
    private LineMessagingClient lineMessagingClient;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception{
        TextMessageContent message = event.getMessage();
        handleTextContent(event.getReplyToken(), event, message);
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) {
        reply(replyToken, singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
        reply(replyToken, messages, false);
    }

    private void reply(@NonNull String replyToken,
                       @NonNull List<Message> messages,
                       boolean notificationDisabled) {
        try {
            BotApiResponse apiResponse = lineMessagingClient
                    .replyMessage(new ReplyMessage(replyToken, messages, notificationDisabled))
                    .get();
            log.info("Sent messages: {}", apiResponse);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Message> makeReplyMessage(String command) throws Exception {
        CommandAction.CommandEnum commandEnum = CommandAction.commandMap.get(command) != null ? CommandAction.commandMap.get(command) : CommandAction.CommandEnum.UNKNOWN_COMMAND;
        return Optional.of(commandEnum.makeReplyMessage());

    }

    private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws Exception{
        final String text = content.getText();
        log.info("Got text message from replyToken:{}: text:{}", replyToken, text);
        this.reply(replyToken, makeReplyMessage(content.getText()).get());
    }



//    @GetMapping("/oauth2callback")
//    public String testAPI(@RequestParam final String code, @RequestParam final String state){
//
//        DbxAuthFinish authFinish;
//        try {
//            authFinish = webAuth.finishFromCode(code, " https://493d4a8a.ngrok.io/oauth2callback");
//        } catch (DbxException ex) {
//            System.err.println("Error in DbxWebAuth.authorize: " + ex.getMessage());
//            System.exit(1);
//            return "";
//        }
//        log.info("Authorization complete.");
//        log.info("- User ID: " + authFinish.getUserId());
//        log.info("- Account ID: " + authFinish.getAccountId());
//        log.info("- Access Token: " + authFinish.getAccessToken());
//
//
//        return "<html><body><script>window.location=\"https://lin.ee/kXqKkiG\"</script></body></html>";
//
//    }
}
