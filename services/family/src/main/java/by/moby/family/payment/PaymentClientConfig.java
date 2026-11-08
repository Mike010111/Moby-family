package by.moby.family.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class PaymentClientConfig {

    @Bean
    RestClient paymentRestClient(RestClient.Builder builder,
                                 @Value("${moby.clients.payment.url}") String baseUrl,
                                 @Value("${moby.clients.payment.connect-timeout:1s}") Duration connectTimeout,
                                 @Value("${moby.clients.payment.read-timeout:3s}") Duration readTimeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
