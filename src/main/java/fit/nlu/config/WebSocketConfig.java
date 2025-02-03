package fit.nlu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Cấu hình destination prefixes cho messages gửi từ server đến clients
        config.enableSimpleBroker(
                "/topic",  // Cho public broadcasts
                "/queue"
        );
        // Prefix cho messages gửi từ clients đến server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint cho WebSocket connection
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Cho phép cross-origin
                .withSockJS();  // Hỗ trợ fallback nếu WebSocket không khả dụng
    }

}
