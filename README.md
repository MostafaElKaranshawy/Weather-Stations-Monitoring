# 🌦️ Weather Stations Monitoring System

A data-intensive IoT pipeline that simulates distributed weather stations streaming high-frequency data to a centralized system for real-time processing, storage, and analytics.

The system demonstrates modern stream processing and distributed systems principles using messaging, efficient storage, and visualization technologies.

## 🏗️ Architecture Overview
<img width="1007" height="427" alt="image" src="https://github.com/user-attachments/assets/65eeadd0-75b7-4144-9a72-0ef4d9090a93" />

The system consists of three main layers:

### 1. Data Acquisition
- Multiple simulated weather stations generate data **every second**
- Each reading includes: temperature, humidity, wind speed, and battery status
- Messages are streamed to **Apache Kafka**

### 2. Data Processing & Storage
- Central service consumes streams from Kafka
- Stores data using two approaches:
  - **Latest readings** → stored in **Bitcask** (key-value store)
  - **Historical data** → stored in **Parquet** files (batched and partitioned)
- Performs real-time stream processing to detect weather events (e.g., rain alerts when humidity > 70%)

### 3. Indexing & Visualization
- Historical data is indexed into **Elasticsearch**
- Visualized through **Kibana** dashboards

## ⚙️ Key Features

-  Real-time data streaming with **Apache Kafka**
-  Stream processing and event detection (rain alerts)
-  Efficient dual storage strategy:
  - **Bitcask** for fast latest-state access
  - **Parquet** for efficient historical analytics
-  Rich visualizations with Kibana dashboards
-  Fully containerized with **Docker**
-  Kubernetes-ready deployment
-  Performance profiling using **Java Flight Recorder (JFR)**

## 🧪 Simulation Details

Each weather station:
- Sends data every second
- Simulates realistic battery status distribution:
  - 30% Low
  - 40% Medium
  - 30% High
- Intentionally drops ~10% of messages to simulate real-world network conditions

## 🚀 Tech Stack

- **Java** (Kafka Producers, Consumers, and Streams API)
- **Apache Kafka**
- **Bitcask** (custom implementation)
- **Parquet** (columnar storage format)
- **Elasticsearch + Kibana**
- **Docker & Kubernetes**
