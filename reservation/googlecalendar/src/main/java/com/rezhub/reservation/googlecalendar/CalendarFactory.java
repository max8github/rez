package com.rezhub.reservation.googlecalendar;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class CalendarFactory {

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    private static final String CREDENTIALS_FILE_PATH = "credentials.json";
    private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";


    /**
     * Build a new authorized Calendar API client service using a service account (two-legged OAuth).
     * @return the Calendar
     * service
     */
    public Calendar create() {
        HttpTransport httpTransport;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
        //Build service account credential
        // Prefer GOOGLE_CREDENTIALS_PATH env var (for Docker/production), fall back to classpath resource (dev)
        HttpRequestInitializer requestInitializer;
        try (InputStream in = credentialsStream()) {
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
            requestInitializer = new HttpCredentialsAdapter(googleCredentials);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private InputStream credentialsStream() throws IOException {
        String envJson = System.getenv("GOOGLE_CREDENTIALS_JSON");
        if (envJson != null && !envJson.isBlank()) {
            return new ByteArrayInputStream(envJson.getBytes(StandardCharsets.UTF_8));
        }
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        return in;
    }

}
