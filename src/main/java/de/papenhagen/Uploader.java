package de.papenhagen;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static java.util.Objects.isNull;

public class Uploader {

    private static final Logger LOGGER = Logger.getLogger(Uploader.class.getName());

    //20MB
    private static final int MAX_FILE_SIZE = 20_000_000;

    private static final String TIKA_URL = System.getProperty("tikaURL");
    private static final String OLLAMA_API_URL = System.getProperty("ollamaURL");
    private static final String VECTOR_URL = System.getProperty("vectorURL");

    public void uploadToTika(final Path path) {
        if (isNull(path)) {
            return;
        }
        LOGGER.info("path: " + path);
        final String fileName = path.getFileName().toString();
        LOGGER.info("fileName: " + fileName);
        try {
            final long fileSize = Files.size(path);
            LOGGER.info("fileSize: " + fileSize);
            if (fileSize > MAX_FILE_SIZE) {
                LOGGER.severe("FileSize to big");
                return;
            }
        } catch (IOException ex) {
            LOGGER.severe("Exception on file size check: " + ex.getLocalizedMessage());
        }

        // Step 1:
        // upload the file to Apache Tika
        // more infos: https://tika.apache.org/
        String textBody = null;
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(isNull(TIKA_URL) ? "http://localhost:9998/tika" : TIKA_URL))
                    .PUT(HttpRequest.BodyPublishers.ofFile(path))
                    .build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            textBody = response.body();
        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("Exception on upload to TIKA: " + ex.getLocalizedMessage());
        }

        LOGGER.info(textBody);

        // Step 2:
        // upload text from the file to an embedding model

        // We use "local" model running on an ollama instance
        // As an embedding model we use: https://www.nomic.ai/blog/posts/nomic-embed-text-v1
        //        "nomic-embed-text" is a large context length text encoder that surpasses
        //        OpenAI "text-embedding-ada-002" and "text-embedding-3-small"
        //        performance on short and long context tasks.
        //TODO: Maybe later we will use a 1b or 3b embedding model to save some RAM and CPU/GPU.
        // Check ollama
        String jsonBody = null;
        try (final HttpClient client = HttpClient.newHttpClient()) {

            final JSONObject ollamaPayload = new JSONObject();
            ollamaPayload.put("model", "nomic-embed-text");
            ollamaPayload.put("prompt", textBody);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(isNull(OLLAMA_API_URL) ? "http://localhost:11434/api/embeddings" : OLLAMA_API_URL))
                    .POST(HttpRequest.BodyPublishers.ofString(ollamaPayload.toJSONString()))
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            jsonBody = response.body();
        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("Exception on upload to OLLAMA: " + ex.getLocalizedMessage());
        }

        LOGGER.info(jsonBody);

        // Step 3:
        // parsing the JSON of the embedding
        final JSONObject data = JSON.parseObject(jsonBody);
        if (isNull(data) || !data.containsKey("embedding")) {
            LOGGER.severe("no embedding found");
            return;
        }
        final JSONArray embedding = data.getJSONArray("embedding");
        final List<Float> embeddingList = embedding.toList(Float.class);
        LOGGER.info("embeddingList size: " + embeddingList.size());

        // Step 4:
        // build up JSON Payload for the next step
        final JSONObject vectorPayload = new JSONObject();

        final JSONObject point = new JSONObject();
        point.put("id", UUID.randomUUID());
        final JSONObject pointPayload = new JSONObject();
        pointPayload.put("filename", fileName);
        point.put("payload", pointPayload);
        point.put("vector", embeddingList);

        final JSONArray array = new JSONArray();
        array.add(point);
        vectorPayload.put("points", array);

        // Step 5:
        // upload the payload into a vector DB
        // We use as vector DB Qdrant
        // more infos: https://qdrant.tech/
        //TODO: Maybe later we will change the vector DB. Check for state of sqlite-vec - https://github.com/asg017/sqlite-vec
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(isNull(VECTOR_URL) ? "http://localhost:6333/collections/test_collection/points?wait=true" : VECTOR_URL))
                    .PUT(HttpRequest.BodyPublishers.ofString(vectorPayload.toJSONString()))
                    .setHeader("Content-Type", "application/json")
                    .build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.body().contains("acknowledged")) {
                LOGGER.info("upload successful");
            } else {
                LOGGER.severe("Some Problems with the upload of the embedding into the vector DB");
            }

        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("Exception on upload to the vector DB: " + ex.getLocalizedMessage());
        }

    }


}
