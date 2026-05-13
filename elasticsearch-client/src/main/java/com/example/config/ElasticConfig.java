package com.example.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;

public class ElasticConfig {

    private static final RestClient restClient;
    private static final ElasticsearchClient elasticsearchClient;

    static {
        restClient = RestClient.builder(
                        new HttpHost("localhost", 9200, "http"))
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(5000)
                        .setSocketTimeout(60000))
                .build();

        RestClientTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());

        elasticsearchClient = new ElasticsearchClient(transport);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Closing Elasticsearch client...");
                restClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    public static ElasticsearchClient getClient() {
        return elasticsearchClient;
    }

    public static void close() {
        try {
            restClient.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}