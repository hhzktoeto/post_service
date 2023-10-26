package faang.school.postservice.messaging.kafka.listener;

import faang.school.postservice.messaging.kafka.events.CommentEvent;
import faang.school.postservice.service.RedisCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentConsumer {

    private final RedisCommentService redisCommentService;

    @KafkaListener(topics = "${spring.kafka.channels.comment_event_channel.name}",
            groupId = "${spring.kafka.consumer.group}")
    public void listen(CommentEvent message, Acknowledgment ack) {
        log.info("Event (id: {}; postId: {}) has been delivered (Kafka)", message.getId(), message.getPostId());
        redisCommentService.addCommentToPost(message);
        ack.acknowledge();
    }
}
