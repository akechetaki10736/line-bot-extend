package org.sideproject.linebot.service;
import com.dropbox.core.*;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;
import java.util.*;


@Service
public class DropboxServiceImpl implements Oauth2Service{
    private final Logger log = LoggerFactory.getLogger(DropboxServiceImpl.class);
    private Map<String, DbxWebAuth> dpxAuthMap;
    private Map<String, DbxAuthFinish> dpxAccessMap;
    private DbxAppInfo appInfo;
    private DbxRequestConfig requestConfig;

    @Value("${dropbox.config.app-key}")
    private String dpxAppkey;

    @Value("${dropbox.config.app-secret}")
    private String dpxAppSecret;

    @Value("${dropbox.config.redirect-uri}")
    private String redirectURI;

    public DropboxServiceImpl( ) {
        this.dpxAuthMap =  new HashMap<>();
        this.dpxAccessMap = new HashMap<>();
        this.requestConfig = new DbxRequestConfig("line-bot-dropbox-authorize");
    }

    @PostConstruct
    private void init() {
        this.appInfo = new DbxAppInfo(dpxAppkey, dpxAppSecret);
    }

    @Override
    public String getLoginURI(String lineUserId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpSession httpSession = attributes.getRequest().getSession(true);
        DbxWebAuth webAuth = new DbxWebAuth(requestConfig, appInfo);
        DbxWebAuth.Request webAuthRequest = DbxWebAuth.newRequestBuilder()
                .withRedirectUri(redirectURI, new DbxStandardSessionStore(httpSession, dpxAppkey))
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
            authFinish = dbxWebAuth.finishFromCode(code, redirectURI);
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
    public Optional<List<String>> getFilesList(String userId) throws DbxException {
        List<String> ret = null;
        if(this.dpxAccessMap.get(userId) == null) {
            log.error("User({}) has not yet logged in.", userId);
            return Optional.empty();
        }
        DbxClientV2 dpxClient = new DbxClientV2(requestConfig, this.dpxAccessMap.get(userId).getAccessToken());
        ret = new ArrayList<String>(2);
        StringBuilder fileListBuilder = new StringBuilder(), folderListBuilder = new StringBuilder();
        ListFolderResult result = dpxClient.files().listFolder("");
        while(true) {
            for (Metadata metadata : result.getEntries()) {
                String mtName = metadata.getName();
                if (metadata instanceof FolderMetadata) {
                    //folder here
                    folderListBuilder.append(String.format("folder : %s\n", mtName));
                }
                else {
                    //file here
                    fileListBuilder.append(String.format("file : %s\n", mtName));
                }
            }
            if(!result.getHasMore())
                break;
            result = dpxClient.files().listFolderContinue(result.getCursor());
        }
        ret.add(0, folderListBuilder.toString());
        ret.add(1, fileListBuilder.toString());
        return Optional.of(ret);
    }
}
