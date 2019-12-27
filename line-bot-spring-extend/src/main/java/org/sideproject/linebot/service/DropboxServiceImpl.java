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
    private Map<String, DbxClientV2> dpxClientMap;
    private Map<String, String> dpxUserPWD;
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
        this.dpxClientMap = new HashMap<>();
        this.dpxUserPWD = new HashMap<>();
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
    public void finishOauth(final String code, final String state) throws Exception {
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

        DbxClientV2 dpxClient = new DbxClientV2(requestConfig, authFinish.getAccessToken());
        dpxClientMap.put(userId, dpxClient);
        dpxUserPWD.put(userId, "");

        log.info("Authorization complete.");
        log.info("- User ID: " + authFinish.getUserId());
        log.info("- State: " + userId);
        log.info("- Account ID: " + authFinish.getAccountId());
        log.info("- Access Token: " + authFinish.getAccessToken());

        try {
            dpxClient.files().createFolderV2("/LineSpace");
        } catch (DbxException ex) {
            log.error("Create line folder failed : " + ex.getLocalizedMessage());
        }
    }

    public Optional<List<String>> getFilesList(String userId) throws DbxException {
        List<String> results = null;
        if(!this.dpxClientMap.containsKey(userId)) {
            log.error("User({}) has not yet logged in.", userId);
            return Optional.empty();
        }

        results = new ArrayList<String>(3);
        DbxClientV2 dpxClient = this.dpxClientMap.get(userId);

        StringBuilder fileListBuilder = new StringBuilder(), folderListBuilder = new StringBuilder();
        ListFolderResult resultList = dpxClient.files().listFolder(dpxUserPWD.get(userId));
        results.add(0, dpxUserPWD.get(userId));
        while(true) {
            for (Metadata metadata : resultList.getEntries()) {
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
            if(!resultList.getHasMore())
                break;
            resultList = dpxClient.files().listFolderContinue(resultList.getCursor());
        }
        results.add(1, folderListBuilder.toString());
        results.add(2, fileListBuilder.toString());
        return Optional.of(results);
    }

    public Optional<String> forwardToSpecificFolder(String userId, String path) {
        if(!this.dpxClientMap.containsKey(userId)) {
            log.error("User({}) has not yet logged in.", userId);
            return Optional.empty();
        }
        DbxClientV2 dbxClient = dpxClientMap.get(userId);
        String currentPath = this.dpxUserPWD.get(userId);
        String targetPath = currentPath + "/" +path;
        try {
            dbxClient.files().listFolder(targetPath);
        } catch (DbxException e) {
            log.error("Folder({}) doesn't exist.", path);
            return  Optional.empty();
        }

        String newWorkingDirectory = targetPath;
        this.dpxUserPWD.put(userId, newWorkingDirectory);

        return Optional.of(newWorkingDirectory);
    }

    public Optional<String> backToPreviousFolder(String userId) throws DbxException{
        if(!this.dpxClientMap.containsKey(userId)) {
            log.error("User({}) has not yet logged in.", userId);
            return Optional.empty();
        }
        DbxClientV2 dbxClient = dpxClientMap.get(userId);
        String path = dpxUserPWD.get(userId);
        String pathOfPreviousFolder = path.equals("") ? "" : path.substring(0, path.lastIndexOf('/'));
        dbxClient.files().listFolder(pathOfPreviousFolder);
        dpxUserPWD.put(userId, pathOfPreviousFolder);
        return Optional.of(pathOfPreviousFolder);
    }

    public Optional<String> getFileLink(String userId, String file) {
        String result = null;

        if(!this.dpxClientMap.containsKey(userId) || !this.dpxUserPWD.containsKey(userId))
            return Optional.empty();

        DbxClientV2 dpxClient = this.dpxClientMap.get(userId);

        StringBuilder sb = new StringBuilder();
        sb.append("/" + this.dpxUserPWD.get(userId));

        if(!this.dpxUserPWD.get(userId).equals(""))
            sb.append("/");

        sb.append(file);

        try {
            result = dpxClient.files().getTemporaryLink(sb.toString()).getLink();
        } catch (DbxException ex) {
            log.error(ex.getMessage());
        }

        if(result == null)
            return Optional.empty();

        return Optional.of(result);
    }
}
