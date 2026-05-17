package com.example.consumer;

import com.example.exception.ValidationException;
import com.example.idempotency.IdempotentReceiver;
import com.example.model.WeatherRecord;
import com.example.router.EnvelopeUnwrapper;
import com.example.router.FailureMessageRouter;
import com.example.storage.WeatherStorageCoordinator;
import com.example.validation.MessageValidator;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class WeatherKafkaConsumer implements Runnable, AutoCloseable {

    static Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final String TOPIC = dotenv.get("KAFKA_TOPIC") != null ? dotenv.get("KAFKA_TOPIC") : System.getenv().getOrDefault("KAFKA_TOPIC", "");
    private static final int MAX_RETRIES = Integer.parseInt(dotenv.get("MAX_RETRIES") != null ? dotenv.get("MAX_RETRIES") : System.getenv().getOrDefault("MAX_RETRIES", ""));

    private final KafkaConsumer<String, String> consumer;

    // validates message structure and field values before we do anything else
    private final MessageValidator validator;

    // needed to parse the received JSON into WeatherRecord object
    private final EnvelopeUnwrapper unwrapper;

    // routes bad messages to weather_invalid_data or weather_dead_letter
    private final FailureMessageRouter failureRouter;

    // writes valid records to BitCask (latest) and Parquet (full archive)
    private final WeatherStorageCoordinator storageCoordinator;

    private final IdempotentReceiver idempotentReceiver;

    private volatile boolean running = true;

    // NEED TO REMOVE ALL THE HARD CODED PARAMETERS, AND PASS THEM FROM OUTSIDE, EITHER VIA CONSTRUCTOR OR CONFIG FILE
    public WeatherKafkaConsumer(WeatherStorageCoordinator storageCoordinator) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, dotenv.get("KAFKA_CONSUMER_GROUP_ID") != null ? dotenv.get("KAFKA_CONSUMER_GROUP_ID") : System.getenv().getOrDefault("KAFKA_CONSUMER_GROUP_ID", ""));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // If this consumer group has no committed offset yet (first run),
        // start reading from the very beginning of the topic
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Auto-commit runs on a timer — it could commit an offset before we
        // finish writing to BitCask/Parquet. If we crash in that gap, the
        // message is lost forever. Manual commit means we only advance the
        // offset after the record is safely on disk.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");


        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC));

        this.validator = new MessageValidator();
        this.failureRouter = new FailureMessageRouter();
        this.unwrapper = new EnvelopeUnwrapper();
        this.idempotentReceiver = new IdempotentReceiver();

        // needs to be passed from outside to prevent coupling
        this.storageCoordinator = storageCoordinator;
    }


    @Override
    public void run() {
        System.out.println("[Consumer] started, listening on: " + TOPIC);

        try {
            while (running) {
                // Polling consumer pattern:
                // poll() asks Kafka "give me any available messages, wait up to
                // 500ms if there are none right now."
                // It returns a batch — could be 0, 1, or many records.
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                try {
                    for (ConsumerRecord<String, String> record : records) {
                        boolean success = processOneRecordWithRetry(record);

                        if (!success)
                            failureRouter.sendToDeadLetterChannel(record.value());
                    }
                }
                catch (Exception e) {
                    System.out.println("[Consumer] error processing batch: " + e.getMessage());
                }
                // If we actually received something and processed it, then we should move the offset forward.
                // If it's an empty batch, we just loop back and poll again without committing. And If failure happened,
                // the whole batch will be resent so it should be handled at IdempotentReceiver (Yarab Ya3ny)
                if (!records.isEmpty())
                    consumer.commitSync();

            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            // This is an expected exception thrown by consumer.poll() when consumer.wakeup() is executed.
            // It breaks us out of the while loop cleanly to allow graceful termination without errors.
            System.out.println("[Consumer] Received wakeup signal. Stopping poll loop smoothly...");
        } finally {
            System.out.println("[Consumer] Polling loop fully terminated.");
        }
    }

    private boolean processOneRecordWithRetry(ConsumerRecord<String, String> record) {

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                processOneRecord(record);
                return true;
            } catch (ValidationException e) {
                failureRouter.sendToInvalidChannel(record.value());
                return true;
            } catch (Exception e) {
                // sleep for a while before retrying
                sleepBackoff(attempt);
            }
        }

        return false;
    }

    private void processOneRecord(ConsumerRecord<String, String> record) throws Exception {
        String rawJSON = record.value();

        // 1. validate and route to failure topics if needed
        if (!validator.isValidJSON(rawJSON))
            throw new ValidationException();


        // 2. parse JSON into WeatherRecord object
        // Optional for the case my checks are damg (if I forgot anything ya3ny)
        Optional<WeatherRecord> maybeRecord = unwrapper.unwrap(rawJSON);
        if (maybeRecord.isEmpty())
            throw new ValidationException();


        WeatherRecord weatherRecord = maybeRecord.get();

        // 3. check for duplicate deliveries
        if (!idempotentReceiver.shouldProcess(weatherRecord.getStationId(), weatherRecord.getSNo())) {
            System.out.printf("[Consumer] skipping duplicate message for station %d with sNo %d%n",
                    weatherRecord.getStationId(), weatherRecord.getSNo());
            return;
        }

        // 4. save to storage (BitCask + Parquet)
        // [Probably] Claim Check pattern (implemented inside MessageDispatcher): ---> need to double check this
        // BitCask  → stores only the LATEST value per station (fast lookups)
        // Parquet  → stores EVERY message (full historical archive)
        storageCoordinator.save(weatherRecord);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); // restore interrupt flag, don't swallow it
        }
    }

    public void stop() {
        running = false;
        consumer.wakeup(); // unblocks poll() immediately instead of waiting 500ms
    }

    @Override
    public void close() {
        try {
            failureRouter.close();
        } catch (Exception e) {
            System.err.println("[Consumer] Error closing failureRouter: " + e.getMessage());
        }
        System.out.println("[Consumer] Closing Kafka consumer connection...");
        consumer.close();
    }

}
