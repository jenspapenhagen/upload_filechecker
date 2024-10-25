package de.papenhagen;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.logging.Logger;

import static java.util.Objects.isNull;

public class Uploader {

    private static final Logger LOGGER = Logger.getLogger(Uploader.class.getName());

    final String tikaURL = System.getProperty("tikaURL");

    //TODO:
    // upload to tika service
    // upload the response into the embedding ollama instant
    // upload the embedding string with all the meta data from the file into an vector DB

    public void uploadToTika(final Path path) {

        String jsonBody = null;
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(isNull(tikaURL) ? "http://localhost:9998/tika" : tikaURL))
                    .PUT(HttpRequest.BodyPublishers.ofFile(path))
                    .setHeader("Accept", "application/json")
                    .build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            jsonBody = response.body();
        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("Exception on upload to TIKA" + ex.getLocalizedMessage());
        }


    }

}
