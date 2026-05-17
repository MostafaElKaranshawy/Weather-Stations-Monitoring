- Elastic Search Image

```
docker pull docker.elastic.co/elasticsearch/elasticsearch:8.13.4
```

```
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  docker.elastic.co/elasticsearch/elasticsearch:8.13.4

```

- creating the index
```
curl -X PUT "http://localhost:9200/weather-records" \
  -H "Content-Type: application/json" \
  -d '{"settings":{"number_of_shards":1,"number_of_replicas":0}}'
```

- if an error occurred (because of double index), delete all indices created and recreate the index again.
```
curl -X DELETE "http://localhost:9200/weather-records"
curl -X PUT "http://localhost:9200/weather-records" \
  -H "Content-Type: application/json" \
  -d '{"settings":{"number_of_shards":1,"number_of_replicas":0}}'
```

- check number of created indices
```
curl http://localhost:9200/weather-records/_count?pretty
```

- Kibana UI
```
docker pull docker.elastic.co/kibana/kibana:8.13.4
```

```
docker run -d \
  --name kibana \
  --link elasticsearch:elasticsearch \
  -p 5601:5601 \
  -e "ELASTICSEARCH_HOSTS=http://elasticsearch:9200" \
  docker.elastic.co/kibana/kibana:8.13.4
```
**wait few seconds then open http://localhost:5601/**

- Dropped Messages Metric

```
(max(sno) - min(sno)+1 - count())/count() * 100
```