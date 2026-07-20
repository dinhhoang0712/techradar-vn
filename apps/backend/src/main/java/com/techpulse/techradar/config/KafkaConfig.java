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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kafka configuration for Spring Boot.
 *
 * This configuration creates the producer, consumer, and required topics
 * so the Spring application can send and receive Kafka messages.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /** Declarative name/partitions/replicas spec for a topic; see {@link #TOPIC_SPECS}. */
    private record TopicSpec(String name, int partitions, short replicas) {
    }

    /**
     * Single source of truth for every topic this application declares. Each {@code @Bean
     * NewTopic} method below simply looks up its spec here by name, so adding/changing a topic's
     * partitions or replicas only requires editing this list.
     */
    private static final List<TopicSpec> TOPIC_SPECS = List.of(
            new TopicSpec(KafkaTopicConstants.RAW_ARTICLES, 3, (short) 1),
            new TopicSpec(KafkaTopicConstants.RAW_JOBS, 3, (short) 1),
            new TopicSpec(KafkaTopicConstants.EXTRACTED_ARTICLES, 3, (short) 1),
            new TopicSpec(KafkaTopicConstants.EXTRACTED_JOBS, 3, (short) 1),
            new TopicSpec(KafkaTopicConstants.ARTICLE_VECTORS, 3, (short) 1),
            new TopicSpec(KafkaTopicConstants.JOB_VECTORS, 3, (short) 1),
            new TopicSpec(KafkaTopicConstants.TREND_ALERTS, 1, (short) 1),
            new TopicSpec(KafkaTopicConstants.ROADMAP_ALERTS, 1, (short) 1));

    private static final Map<String, TopicSpec> TOPIC_SPECS_BY_NAME =
            TOPIC_SPECS.stream().collect(Collectors.toMap(TopicSpec::name, Function.identity()));

    private static NewTopic newTopic(String name) {
        TopicSpec spec = TOPIC_SPECS_BY_NAME.get(name);
        if (spec == null) {
            throw new IllegalStateException("No TopicSpec registered for topic '" + name + "'");
        }
        return TopicBuilder.name(spec.name())
                .partitions(spec.partitions())
                .replicas(spec.replicas())
                .build();
    }

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
        return newTopic(KafkaTopicConstants.RAW_ARTICLES);
    }

    @Bean
    public NewTopic rawJobsTopic() {
        return newTopic(KafkaTopicConstants.RAW_JOBS);
    }

    @Bean
    public NewTopic extractedArticlesTopic() {
        return newTopic(KafkaTopicConstants.EXTRACTED_ARTICLES);
    }

    @Bean
    public NewTopic extractedJobsTopic() {
        return newTopic(KafkaTopicConstants.EXTRACTED_JOBS);
    }

    @Bean
    public NewTopic articleVectorsTopic() {
        return newTopic(KafkaTopicConstants.ARTICLE_VECTORS);
    }

    @Bean
    public NewTopic jobVectorsTopic() {
        return newTopic(KafkaTopicConstants.JOB_VECTORS);
    }

    @Bean
    public NewTopic trendAlertsTopic() {
        return newTopic(KafkaTopicConstants.TREND_ALERTS);
    }

    @Bean
    public NewTopic roadmapAlertsTopic() {
        return newTopic(KafkaTopicConstants.ROADMAP_ALERTS);
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
