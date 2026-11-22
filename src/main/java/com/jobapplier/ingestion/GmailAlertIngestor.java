package com.jobapplier.ingestion;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.jobapplier.job.Job;
import com.jobapplier.job.JobSource;
import com.jobapplier.profile.CandidateProfile;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Safer alternative: parse LinkedIn/Naukri/Indeed job-alert emails via Gmail API (ToS-friendly, stable). */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailAlertIngestor implements JobScraper {

    private static final String CREDENTIALS_PATH = "data/credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = "data/tokens";
    private static final String APPLICATION_NAME = "JobApplier";
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_MODIFY);

    private final GmailAlertParser parser;

    @Override
    public JobSource source() {
        return JobSource.GMAIL_ALERT;
    }

    @Override
    public List<Job> fetch(CandidateProfile profile) {
        File credentialsFile = new File(CREDENTIALS_PATH);
        if (!credentialsFile.exists()) {
            log.info("Gmail credentials file not found at '{}'. Skipping Gmail alert ingestion.", CREDENTIALS_PATH);
            return List.of();
        }

        List<Job> allJobs = new ArrayList<>();
        try {
            Gmail service = getGmailService(credentialsFile);
            ListMessagesResponse listResponse = service.users().messages().list("me")
                .setQ("from:(linkedin.com OR naukri.com OR indeed.com) is:unread")
                .setMaxResults(25L)
                .execute();

            List<Message> messages = listResponse.getMessages();
            if (messages == null || messages.isEmpty()) {
                log.info("No unread job alert emails found.");
                return List.of();
            }

            for (Message msgSummary : messages) {
                try {
                    Message fullMsg = service.users().messages().get("me", msgSummary.getId())
                        .setFormat("full")
                        .execute();

                    String from = getHeader(fullMsg, "From");
                    String subject = getHeader(fullMsg, "Subject");
                    String bodyHtml = extractHtmlBody(fullMsg.getPayload());
                    Instant receivedAt = fullMsg.getInternalDate() != null
                        ? Instant.ofEpochMilli(fullMsg.getInternalDate())
                        : Instant.now();

                    List<Job> parsedJobs = parser.parseEmail(from, subject, bodyHtml, receivedAt);
                    allJobs.addAll(parsedJobs);

                    // Mark as processed (remove UNREAD label)
                    ModifyMessageRequest mods = new ModifyMessageRequest()
                        .setRemoveLabelIds(Collections.singletonList("UNREAD"));
                    service.users().messages().modify("me", msgSummary.getId(), mods).execute();
                    log.debug("Processed alert email id='{}', extracted {} jobs", msgSummary.getId(), parsedJobs.size());
                } catch (Exception e) {
                    log.error("Failed processing alert email id='{}': {}", msgSummary.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error connecting to Gmail API: {}", e.getMessage(), e);
        }

        return allJobs;
    }

    private Gmail getGmailService(File credentialsFile) throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
            jsonFactory,
            new InputStreamReader(new FileInputStream(credentialsFile), StandardCharsets.UTF_8)
        );

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            httpTransport, jsonFactory, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();

        com.google.api.client.auth.oauth2.Credential credential = flow.loadCredential("user");
        if (credential == null) {
            throw new IllegalStateException("Gmail OAuth token not authorized yet. Please authorize via desktop flow.");
        }

        return new Gmail.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    private String getHeader(Message message, String name) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return "";
        }
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (name.equalsIgnoreCase(header.getName())) {
                return header.getValue();
            }
        }
        return "";
    }

    private String extractHtmlBody(MessagePart part) {
        if (part == null) return "";

        if (part.getBody() != null && part.getBody().getData() != null) {
            String decoded = new String(Base64.getUrlDecoder().decode(part.getBody().getData()), StandardCharsets.UTF_8);
            if ("text/html".equalsIgnoreCase(part.getMimeType())) {
                return decoded;
            }
            if ("text/plain".equalsIgnoreCase(part.getMimeType()) && (part.getParts() == null || part.getParts().isEmpty())) {
                return decoded;
            }
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                if ("text/html".equalsIgnoreCase(subPart.getMimeType())) {
                    return extractHtmlBody(subPart);
                }
            }
            for (MessagePart subPart : part.getParts()) {
                String result = extractHtmlBody(subPart);
                if (!result.isEmpty()) return result;
            }
        }

        return "";
    }
}
