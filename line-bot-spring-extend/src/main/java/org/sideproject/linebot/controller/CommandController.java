package org.sideproject.linebot.controller;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.*;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sideproject.linebot.command.CommandAction;
import org.sideproject.linebot.service.DropboxServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;
@Slf4j
@LineMessageHandler
@RestController
public class CommandController {

    private LineMessagingClient lineMessagingClient;
    private DropboxServiceImpl dpxServiceImpl;

    private Map<String, String> userCache = new HashMap<>();

    @Autowired
    public CommandController(LineMessagingClient lineMessagingClient, DropboxServiceImpl dpxServiceimpl) {
        this.lineMessagingClient = lineMessagingClient;
        this.dpxServiceImpl = dpxServiceimpl;
    }

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        TextMessageContent message = event.getMessage();
        handleTextContent(event.getReplyToken(), event, message);
    }

    @EventMapping
    public void handleImageMessageContent(MessageEvent<ImageMessageContent> event) throws Exception {
        handleMultiMediaContent (
                event.getReplyToken(),
                event.getMessage().getId(),
                ImageContent -> {
                    try {
                            dpxServiceImpl.uploadImageSteam(event.getSource().getUserId(), ImageContent.getStream(), ImageContent.getLength());
                    } catch (Exception e) {
                        log.error(e.getMessage());
                    }
                });
    }

    @EventMapping
    public void handleAudioMessageContent(MessageEvent<AudioMessageContent> event) throws Exception{
        /* TODO */
    }

    @EventMapping
    public void handleVideoMessageContent(MessageEvent<VideoMessageContent> event) throws Exception{
        /* TODO */
    }

    @EventMapping
    public void handleFileMessageContent(MessageEvent<FileMessageContent> event) throws Exception{
        /* TODO */
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

    private Optional<Message> makeReplyMessage(String command, String userId) throws Exception {

        CommandAction.CommandEnum commandEnum =CommandAction.CommandEnum.UNKNOWN_COMMAND;

        // 0:userId, 1:command, 2: command parameter
        List<String> contextArr = new ArrayList<>();
        contextArr.addAll(Arrays.asList(command.split(" ")));
        contextArr.add(0, userId);

        if(command.startsWith("!") && CommandAction.commandMap.containsKey(contextArr.get(1)))
            commandEnum = CommandAction.commandMap.get(contextArr.get(1));

        return Optional.of(commandEnum.makeReplyMessage(contextArr));
    }

    private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws Exception{
        final String text = content.getText();
        log.info("Got text message from replyToken:{}: text:{}", replyToken, text);
        this.reply(replyToken, makeReplyMessage(content.getText(), event.getSource().getUserId()).get());
    }

    private void handleMultiMediaContent(String replyToken, String messageId, Consumer<MessageContentResponse> msgConsumer) {
        final MessageContentResponse response;
        try {
            response = lineMessagingClient.getMessageContent(messageId)
                    .get();
            log.info("Get content : {}", response);
        } catch (InterruptedException | ExecutionException e) {
            reply(replyToken, new TextMessage("Get content error"));
            log.error("Cannot get content : {}", e.getMessage());
            return;
        }

        msgConsumer.accept(response);
    }
}
