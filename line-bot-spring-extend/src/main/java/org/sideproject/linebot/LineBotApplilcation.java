/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.sideproject.linebot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

@SpringBootApplication
@LineMessageHandler
public class LineBotApplilcation {

    private final Logger log = LoggerFactory.getLogger(LineBotApplilcation.class);
    public static void main(String[] args) {
        SpringApplication.run(LineBotApplilcation.class, args);
    }

//    @EventMapping
//    public Message handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
//        log.info("event: " + event);
//        final String originalMessageText = event.getMessage().getText();
//        return new TextMessage(originalMessageText);
//    }
//
//    @EventMapping
//    public void handleDefaultMessageEvent(Event event) {
//        System.out.println("event: " + event);
//    }
}
