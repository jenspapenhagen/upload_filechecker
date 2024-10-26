package de.papenhagen;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static java.util.Objects.isNull;

public class Uploader {

    private static final Logger LOGGER = Logger.getLogger(Uploader.class.getName());

    static final String TIKA_URL = "http://beta.offenedaten.de:9998/tika";
    //System.getProperty("tikaURL");

    static final String OLLAMA_API_URL = "http://localhost:11434/api/embeddings";

    //TODO:
    // upload to tika service  - DONE
    // upload the response into the embedding ollama instant
    // upload the embedding string with all the meta data from the file into an vector DB

    public void uploadToTika(final Path path) {
        if (isNull(path)) {
            return;
        }
        LOGGER.severe("path:" + path);

        String textBody = null;
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(isNull(TIKA_URL) ? "http://localhost:9998/tika" : TIKA_URL))
                    .PUT(HttpRequest.BodyPublishers.ofFile(path))
                    .build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            textBody = response.body();
        } catch (IOException | InterruptedException ex) {
            LOGGER.severe("Exception on upload to TIKA" + ex.getLocalizedMessage());
        }

        LOGGER.info(textBody);

        // We use a "local" model running in a ollama instance
        // As embedding model we use: https://www.nomic.ai/blog/posts/nomic-embed-text-v1
        //        nomic-embed-text is a large context length text encoder that surpasses OpenAI text-embedding-ada-002
        //        and text-embedding-3-small performance on short and long context tasks.

        //maybe later we will use a 1b or 3b embedding model to save some RAM and CPU/GPU
        String jsonBody = null;
        try (HttpClient client = HttpClient.newHttpClient()) {

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
            LOGGER.severe("Exception on upload to OLLAMA" + ex.getLocalizedMessage());
        }

        LOGGER.info(jsonBody);

        //json parsing of the embedding
        final JSONObject data = JSON.parseObject(jsonBody);
        if (isNull(data) || !data.containsKey("embedding")) {
            LOGGER.severe("no embedding found");
            return;
        }
        final JSONArray embedding = data.getJSONArray("embedding");
        final List<Float> embeddingList = embedding.toList(Float.class);
        LOGGER.info("embeddingList size: " + embeddingList.size());

    }


}
