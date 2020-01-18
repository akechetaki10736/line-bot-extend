# Dropbox Manipulatting with LineBot
Accessing and Manipulating Dropbox by simple text command.
## Tech Stack
Line Message API
Dropbox SDK
Lombok

## Class Responsibility 
* CommandAction: Including implementations of each command.
* CommandController: In cahrge of replying messaging and dispatching command.
* DropboxOAuthController: Handling  OAuth flow.
* DropboxServiceImpl: Implementation of Dropbox relating API.
* OAuthSerivce: Interface of Dropbox OAuth flow.
* LineBotApplication: Main Application.

## What you can do with the bot？
* Login to you Dropbox account.
* Manipulating your cloud file.
* Upload file.
* Get the download link of your cloud files.
