package ru.crpt.api;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        CrptApi crptApi = new CrptApi(Duration.ofSeconds(10), 2);
        List<CrptApi.Product> products = new ArrayList<>();
        CrptApi.Product product = new CrptApi.Product.ProductBuilder()
                .certificateDocument("string")
                .certificateDocumentDate(LocalDate.parse("2020-01-23"))
                .certificateDocumentNumber("string")
                .ownerInn("string")
                .producerInn("string")
                .productionDate(LocalDate.parse("2020-01-23"))
                .tnvedCode("string")
                .uitCode("string")
                .uituCode("string")
                .build();
        products.add(product);
        CrptApi.Document document = new CrptApi.Document.DocumentBuilder()
                .description(new CrptApi.Description("string"))
                .docId("string")
                .docStatus("string")
                .docType("LP_INTRODUCE_GOODS")
                .importRequest(true)
                .ownerInn("string")
                .participantInn("string")
                .producerInn("string")
                .productionDate(LocalDate.parse("2020-01-23"))
                .productionType("string")
                .products(products)
                .regDate(LocalDate.parse("2020-01-23"))
                .regNumber("string")
                .build();
        crptApi.createDocument(document, "sign");
    }
}
