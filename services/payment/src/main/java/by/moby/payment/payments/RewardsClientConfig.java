package by.moby.payment.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class RewardsClientConfig {

    @Bean
    RestClient rewardsRestClient(RestClient.Builder builder,
                                 @Value("${moby.clients.rewards.url}") String baseUrl,
                                 @Value("${moby.clients.rewards.connect-timeout:500ms}") Duration connectTimeout,
                                 @Value("${moby.clients.rewards.read-timeout:1s}") Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
