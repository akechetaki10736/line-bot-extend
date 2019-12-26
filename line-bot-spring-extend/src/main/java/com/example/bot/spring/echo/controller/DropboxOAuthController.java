package com.example.bot.spring.echo.controller;

import com.example.bot.spring.echo.service.Oauth2Service;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DropboxOAuthController {


    private LineMessagingClient lineMessagingClient;
    private Oauth2Service oauth2service;

    @Autowired
    public DropboxOAuthController(LineMessagingClient lineMessagingClient, Oauth2Service oauth2service) {
        this.lineMessagingClient = lineMessagingClient;
        this.oauth2service = oauth2service;
    }

    @Value("${line.bot.channel-link}")
    private String redirectURI;

    @GetMapping("/oauth2callback")
    public String handleOauthCallback(@RequestParam final String code, @RequestParam final String state) {
        oauth2service.finishOauth(code, state);
        String userId = state.substring(state.indexOf("uid=")+4);
        PushMessage cmdReminder = new PushMessage(userId, new TextMessage("Auth successful, plz input next command"));
        lineMessagingClient.pushMessage(cmdReminder);
        return "<html><body><script>window.location=\"" + redirectURI + "\"</script></body></html>";
    }
}
