package dev.reference.commerce.shared.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfiguration {
  @Bean
  org.springframework.kafka.support.ProducerListener<Object, Object> safeProducerListener() {
    return new org.springframework.kafka.support.ProducerListener<>() {
      public void onError(
          org.apache.kafka.clients.producer.ProducerRecord<Object, Object> record,
          org.apache.kafka.clients.producer.RecordMetadata metadata,
          Exception exception) {
        org.slf4j.LoggerFactory.getLogger(KafkaConfiguration.class)
            .warn("Kafka send failed topic={} key={}", record.topic(), record.key());
      }
    };
  }

  @Bean
  NewTopic placedTopic() {
    return new NewTopic("orders.placed.v1", 3, (short) 1)
        .configs(java.util.Map.of("retention.ms", "604800000"));
  }

  @Bean
  NewTopic dltTopic() {
    return new NewTopic("orders.placed.v1.dlt", 3, (short) 1);
  }

  @Bean
  DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, ex) -> new TopicPartition("orders.placed.v1.dlt", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(15));
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500, 2));
    handler.setAckAfterHandle(true);
    return handler;
  }
}
