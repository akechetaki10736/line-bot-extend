package org.sideproject.linebot.service;

import com.dropbox.core.*;
import com.dropbox.core.util.IOUtil;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.*;
import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import org.apache.commons.io.input.CountingInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpSession;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class DropboxServiceImpl implements Oauth2Service{
    private final Logger log = LoggerFactory.getLogger(DropboxServiceImpl.class);
    private Map<String, DbxWebAuth> dpxAuthMap;
    private Map<String, DbxClientV2> dpxClientMap;
    private Map<String, String> dpxUserPWD;
    private DbxAppInfo appInfo;
    private DbxRequestConfig requestConfig;
    private ExecutorService executorService;

    @Autowired
    private LineMessagingClient lineMessagingClient;

    @Value("${dropbox.config.app-key}")
    private String dpxAppkey;

    @Value("${dropbox.config.app-secret}")
    private String dpxAppSecret;

    @Value("${dropbox.config.redirect-uri}")
    private String redirectURI;

    @Value("${dropbox.config.chunk-size}")
    private String chunkSize;
    private long CHUNKED_UPLOAD_CHUNK_SIZE;

    private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;

    public DropboxServiceImpl( ) {
        this.dpxAuthMap =  new HashMap<>();
        this.dpxClientMap = new HashMap<>();
        this.dpxUserPWD = new HashMap<>();
        this.requestConfig = new DbxRequestConfig("line-bot-dropbox-authorize");
    }

    @PostConstruct
    private void init() {
        try {
            CHUNKED_UPLOAD_CHUNK_SIZE = Long.parseLong(chunkSize);
            CHUNKED_UPLOAD_CHUNK_SIZE = CHUNKED_UPLOAD_CHUNK_SIZE << 20 ; // n MiB
        } catch (NumberFormatException ex) {
            log.error(ex.getLocalizedMessage());
            System.exit(1);
        }
        this.appInfo = new DbxAppInfo(dpxAppkey, dpxAppSecret);
        executorService = Executors.newFixedThreadPool(16);
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
            dpxClient.files().createFolderV2("/LineSpace/Picture");
            dpxClient.files().createFolderV2("/LineSpace/Audio");
            dpxClient.files().createFolderV2("/LineSpace/Video");
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

        StringBuilder targetFilePath = new StringBuilder();

        targetFilePath.append(this.dpxUserPWD.get(userId));

        targetFilePath.append("/" + file);

        try {
            result = dpxClient.files().getTemporaryLink(targetFilePath.toString()).getLink();
        } catch (DbxException ex) {
            log.error(ex.getMessage());
        }

        if(result == null)
            return Optional.empty();

        return Optional.of(result);
    }


    private class UploadThreadPoolRunnable implements Runnable, IOUtil.ProgressListener {
        private String userId;
        private DbxClientV2 dpxClient;
        private InputStream inputStream;
        private String dropboxPath;
        private long fileSize;
        private long uploadedSize = 0;

        public UploadThreadPoolRunnable(String userId, DbxClientV2 dpxClient, InputStream inputStream, String dropboxPath, long fileSize) {
            this.userId = userId;
            this.dpxClient = dpxClient;
            this.inputStream = inputStream;
            this.dropboxPath = dropboxPath;
            this.fileSize = fileSize;
        }

        @Override
        public void onProgress(long bytesWritten) {
            log.info(String.format("Uploaded %12d / %12d bytes (%5.2f%%)", uploadedSize + bytesWritten, fileSize, 100 * ( (uploadedSize + bytesWritten) / (double) fileSize)));
        }

        private void updateCurrentUploadedSize(long bytes) {
            uploadedSize = bytes;
        }

        @Override
        public void run() {
            FileMetadata metadata = null;
            try {
                if(fileSize < CHUNKED_UPLOAD_CHUNK_SIZE) {
                    // no need to upload in chunks
                    metadata = dpxClient.files().uploadBuilder(dropboxPath.toString())
                            .withMode(WriteMode.ADD)
                            .uploadAndFinish(inputStream, (long bytesWritten) -> {
                                log.info(String.format("Uploaded %12d / %12d bytes (%5.2f%%)", bytesWritten, fileSize,  100 * ((bytesWritten) / (double) fileSize)));
                            });
                } else {
                    log.info("This file with {} bytes is larger than {} Mib. Using chunk file upload.", fileSize, chunkSize);
                    String sessionId = null;
                    long uploaded = 0L;
                    CountingInputStream countingInputStream = new CountingInputStream(inputStream);
                    for (int i = 0; i < CHUNKED_UPLOAD_MAX_ATTEMPTS; ++i) {
                        try {
                            countingInputStream.resetByteCount();
                            countingInputStream.skip(uploaded);
                            // Phase 1: Start
                            if (sessionId == null) {
                                sessionId = dpxClient.files().uploadSessionStart()
                                        .uploadAndFinish(countingInputStream, CHUNKED_UPLOAD_CHUNK_SIZE, this)
                                        .getSessionId();
                                uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                                updateCurrentUploadedSize(uploaded);
                            }

                            UploadSessionCursor cursor = new UploadSessionCursor(sessionId, uploaded);

                            // Phase 2: Append
                            while ((fileSize - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                                dpxClient.files().uploadSessionAppendV2(cursor)
                                        .uploadAndFinish(countingInputStream, CHUNKED_UPLOAD_CHUNK_SIZE, this);
                                uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                                updateCurrentUploadedSize(uploaded);
                                cursor = new UploadSessionCursor(sessionId, uploaded);
                            }

                            // Phase 3: Finish
                            long remaining = fileSize - uploaded;
                            CommitInfo commitInfo = CommitInfo.newBuilder(dropboxPath.toString())
                                    .withMode(WriteMode.ADD)
                                    .build();
                            metadata = dpxClient.files().uploadSessionFinish(cursor, commitInfo)
                                    .uploadAndFinish(countingInputStream, remaining, this);
                        } catch (RetryException ex) {
                            log.warn("Retrying upload process in {} seconds...", ex.getBackoffMillis() / 1000);
                            Thread.sleep(ex.getBackoffMillis());
                            continue;
                        } catch (NetworkIOException ex) {
                            log.warn("Network error({}) occurred.", ex.getMessage());
                            continue;
                        } catch (UploadSessionLookupErrorException ex) {
                            log.error("server offset({}) into the stream doesn't match our offset ({}).", ex.errorValue.getIncorrectOffsetValue().getCorrectOffset(), uploaded);
                            throw ex;
                        } catch (UploadSessionFinishErrorException ex) {
                            if (ex.errorValue.isLookupFailed() && ex.errorValue.getLookupFailedValue().isIncorrectOffset()) {
                                // server offset into the stream doesn't match our offset (uploaded). Seek to
                                // the expected offset according to the server and try again.
                                uploaded = ex.errorValue
                                        .getLookupFailedValue()
                                        .getIncorrectOffsetValue()
                                        .getCorrectOffset();
                                continue;
                            }
                             else
                                throw ex;
                        }
                    }
                }

            } catch (Exception e) {
                lineMessagingClient.pushMessage(new PushMessage(
                        userId, new TextMessage(String.format("Upload file failed. Message=%s",e.getMessage()))
                ));
                 return;
            }
            lineMessagingClient.pushMessage(new PushMessage(
                    userId, new TextMessage("Upload file to dropbox successfully.")
            ));
        }
    }

    public void uploadFileStream(String userId, InputStream inputStream, long fileSize, String absolutePath) throws Exception {
        if(!this.dpxClientMap.containsKey(userId) || !this.dpxUserPWD.containsKey(userId)) {
            log.error("User might not login yet");
            throw new IllegalAccessException("Please login to your dropbox first!");
        }

        DbxClientV2 dpxClient = this.dpxClientMap.get(userId);

        StringBuilder dropboxPath = new StringBuilder();
        dropboxPath.append(absolutePath);

        executorService.submit(new UploadThreadPoolRunnable(userId, dpxClient, inputStream, dropboxPath.toString(), fileSize));

        //log.info("Finished uploading file :\n {}", metadata.toStringMultiline());
    }
}
