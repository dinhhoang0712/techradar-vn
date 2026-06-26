package com.techpulse.techradar.config;

import com.techpulse.techradar.features.kafka.KafkaTopicConstants;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.TopicBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for Spring Boot.
 *
 * This configuration creates the producer, consumer, and required topics
 * so the Spring application can send and receive Kafka messages.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:techradar-group}")
    private String consumerGroupId;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic rawArticlesTopic() {
        return TopicBuilder.name(KafkaTopicConstants.RAW_ARTICLES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rawJobsTopic() {
        return TopicBuilder.name(KafkaTopicConstants.RAW_JOBS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic extractedArticlesTopic() {
        return TopicBuilder.name(KafkaTopicConstants.EXTRACTED_ARTICLES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic extractedJobsTopic() {
        return TopicBuilder.name(KafkaTopicConstants.EXTRACTED_JOBS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic articleVectorsTopic() {
        return TopicBuilder.name(KafkaTopicConstants.ARTICLE_VECTORS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobVectorsTopic() {
        return TopicBuilder.name(KafkaTopicConstants.JOB_VECTORS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic trendAlertsTopic() {
        return TopicBuilder.name(KafkaTopicConstants.TREND_ALERTS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Fail fast when the broker is unreachable so producing (e.g. trend alerts from the ETL)
        // never blocks a worker for the default 60s.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        return factory;
    }
}
