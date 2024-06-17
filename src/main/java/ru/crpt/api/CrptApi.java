package ru.crpt.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.*;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrptApi {
    @Getter
    private static final String API_VERSION = "3";
    @Getter
    private static final String API_HOST = "ismp.crpt.ru";
    @Getter
    private static final String API_ADDRESS = "https://" + API_HOST + "/api/v" + API_VERSION;
    private final JsonSerializer json = new JacksonSerializer();
    private final HttpClient httpClient = new ApacheHttpClient();
    private final RateLimiter rateLimiter;

    /**
     * @param timeLimit    интервал времени.
     * @param requestLimit максимальное количество запросов в заданный интервал времени.
     */
    public CrptApi(Duration timeLimit, int requestLimit) {
        rateLimiter = new Bucket4jRateLimiter(timeLimit, requestLimit);
    }

    /**
     * Создание документа через API Честный знак.
     *
     * @param document  данные для документа.
     * @param signature подпись для документа.
     */
    public void createDocument(Document document, String signature) {
        try {
            rateLimiter.blockingConsume();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Signature", signature);
        httpClient.post(API_ADDRESS + "/lk/documents/create", json.serialize(document), headers);
    }

    /**
     * Интерфейс для добавлени абстракции над библиотекой ограничение запросов.
     */
    public interface RateLimiter {
        void blockingConsume() throws InterruptedException;
    }

    /**
     * Реализация ограничения запросов через библиотеку Bucket4j
     */
    public static class Bucket4jRateLimiter implements RateLimiter {
        private final Bucket bucket;

        public Bucket4jRateLimiter(Duration timeLimit, int requestLimit) {
            bucket = Bucket.builder().addLimit(Bandwidth.classic(requestLimit, Refill.intervally(requestLimit, timeLimit))).build();
        }

        @Override
        public void blockingConsume() throws InterruptedException {
            bucket.asBlocking().consume(1);
        }
    }

    /**
     * Класс для добавления абстракции над библиотекой HTTP клиента
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    private final static class ClientResponse {
        private final int statusCode;
        private final String body;
        private final Map<String, String> headers;

        public Map<String, String> getHeaders() {
            return new HashMap<>(headers);
        }
    }

    /**
     * Интерфейс для добавления абстракции над библиотекой HTTP клиента
     */
    public interface HttpClient {
        /**
         * Выполнить post HTTP-запрос
         *
         * @param uri     адресс запроса.
         * @param body    тело запроса.
         * @param headers заголовки запроса.
         */
        ClientResponse post(String uri, String body, Map<String, String> headers);

        /**
         * Выполнить get HTTP-запрос
         *
         * @param uri     адресс запроса.
         * @param headers заголовки запроса.
         */
        ClientResponse get(String uri, Map<String, String> headers);
    }

    /**
     * Реализация http клиента через библиотеку Apache HttpComponents
     */
    public static class ApacheHttpClient implements HttpClient {
        private final CloseableHttpClient httpClient = HttpClients.createDefault();

        @Override
        public ClientResponse post(String uri, String body, Map<String, String> headers) {
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(httpPost::setHeader);
            }
            try {
                return convertApacheHttpResponse(httpClient.execute(httpPost));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ClientResponse get(String uri, Map<String, String> headers) {
            HttpGet httpGet = new HttpGet(uri);
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(httpGet::setHeader);
            }
            try {
                return convertApacheHttpResponse(httpClient.execute(httpGet));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private ClientResponse convertApacheHttpResponse(HttpResponse response) throws IOException {
            int statusCode = response.getStatusLine().getStatusCode();
            String content = response.getEntity().getContent().toString();
            Map<String, String> responseHeaders = new HashMap<>();
            for (Header header : response.getAllHeaders()) {
                responseHeaders.put(header.getName(), header.getValue());
            }
            return new ClientResponse(statusCode, content, responseHeaders);
        }
    }

    /**
     * Класс для добавления абстракции над библиотекой работы с форматом JSON
     */
    public static abstract class JsonSerializer {
        /**
         * Преобразования объекта в формат JSON.
         *
         * @param o объект преобразования.
         * @return JSON-строка.
         */
        public abstract String serialize(Object o);

        /**
         * Преобразование JSON-строки в объект, соответствующего класса.
         *
         * @param json  JSON-строка.
         * @param clazz класс объекта.
         * @return объект, созданный на основе JSON-строки.
         */
        public abstract <T> T deserialize(String json, Class<T> clazz);
    }

    /**
     * Реализация работы с форматом JSON через библиотеку Jackson
     */
    public static class JacksonSerializer extends JsonSerializer {
        private final ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule()).build()
                .addMixIn(Description.class, SpecificDescription.class)
                .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        @Override
        public String serialize(Object o) {
            try {
                return objectMapper.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> T deserialize(String json, Class<T> clazz) {
            try {
                return objectMapper.readValue(json, clazz);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Не рекомендую использовать разные стратегии наименования
     * с учетом того, что между Document, который использует другую стратегию наимнеования,
     * и Description композитное агрегирование.
     * Возможно опечатка в примере тестового задании, но решил все равно реализовать.
     * Также в примере тестового задания было непонятное число 109 перед "importRequest"
     * Это нарушает формат JSON, поэтому не стал реализовывать,
     * как и специально для "importRequest" lowerCamel стратегию наименования,
     * когда во всех остальных полях класса Document стратегия snake.
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static abstract class SpecificDescription {
    }

    /**
     * Модель описания документа
     */
    @Data
    @AllArgsConstructor
    public static class Description {
        private String participantInn;
    }

    /**
     * Модель продукта документа
     */
    @Data
    @AllArgsConstructor
    @Builder
    public static class Product {
        private String certificateDocument;
        private LocalDate certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private LocalDate productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;


    }

    /**
     * Модель документа
     */
    @Data
    @AllArgsConstructor
    @Builder
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private LocalDate productionDate;
        private String productionType;
        private List<Product> products;
        private LocalDate regDate;
        private String regNumber;
    }
}
