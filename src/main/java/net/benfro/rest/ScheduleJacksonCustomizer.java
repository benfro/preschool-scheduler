package net.benfro.rest;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.quarkus.jackson.ObjectMapperCustomizer;

/**
 * Three of {@link net.benfro.scheduler.domain.SlotActivity}'s four variants ({@code Break},
 * {@code PlanningTime}, {@code OffDuty}) are zero-component marker records - Jackson's
 * default {@code FAIL_ON_EMPTY_BEANS} treats a type with no serializable properties as a
 * configuration mistake and throws, even though the {@code @JsonTypeInfo} "type"
 * discriminator on the sealed interface still gets written correctly. Disabling that one
 * feature is enough; every other default stays as Quarkus configures it.
 */
@Singleton
public class ScheduleJacksonCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
