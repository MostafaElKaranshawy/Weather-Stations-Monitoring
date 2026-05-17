package com.example.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.nodes.Ingest;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;

public class ElasticConfig {

    static Dotenv dotenv = Dotenv.load();
    static String HOST = dotenv.get("ELASTICSEARCH_HOSTNAME");
    static Integer PORT = Integer.parseInt(dotenv.get("ELASTICSEARCH_PORT"));

    private static final RestClient restClient;
    private static final ElasticsearchClient elasticsearchClient;

    static {
        restClient = RestClient.builder(
                        new HttpHost(HOST, PORT, "http"))
                .setRequestConfigCallback(config -> config
                        .setConnectTimeout(5000)
                        .setSocketTimeout(60000))
                .build();

        RestClientTransport transport =
                new RestClientTransport(restClient, new JacksonJsonpMapper());

        elasticsearchClient = new ElasticsearchClient(transport);

    }

    public static ElasticsearchClient getClient() {
        return elasticsearchClient;
    }

    public static void close() {
        try {
            restClient.close();
            System.out.println("Elasticsearch client closed successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}