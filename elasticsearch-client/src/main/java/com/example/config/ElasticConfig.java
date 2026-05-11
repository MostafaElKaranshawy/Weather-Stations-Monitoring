package com.example.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class ElasticConfig {

    public static ElasticsearchClient getClient() {

        RestClient restClient = RestClient.builder(
                        new HttpHost("localhost", 9200))
                .build();

        RestClientTransport transport =
                new RestClientTransport(
                        restClient,
                        new JacksonJsonpMapper());

        return new ElasticsearchClient(transport);
    }
}