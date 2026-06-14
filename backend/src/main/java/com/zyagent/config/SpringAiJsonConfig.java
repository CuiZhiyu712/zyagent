package com.zyagent.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class SpringAiJsonConfig {
    @Bean
    RestClientCustomizer deepSeekRestClientJsonCustomizer(ObjectMapper objectMapper) {
        ObjectMapper tolerantMapper = tolerantMapper(objectMapper);
        return builder -> builder.messageConverters(converters -> {
            converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
            converters.add(new MappingJackson2HttpMessageConverter(tolerantMapper));
        });
    }

    @Bean
    WebClientCustomizer deepSeekWebClientJsonCustomizer(ObjectMapper objectMapper) {
        ObjectMapper tolerantMapper = tolerantMapper(objectMapper);
        return builder -> builder.codecs(configurer -> {
            configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(tolerantMapper));
            configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(tolerantMapper));
        });
    }

    private ObjectMapper tolerantMapper(ObjectMapper objectMapper) {
        return objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
