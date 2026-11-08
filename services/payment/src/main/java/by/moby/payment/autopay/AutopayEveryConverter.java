package by.moby.payment.autopay;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class AutopayEveryConverter implements AttributeConverter<AutopayEvery, String> {

    @Override
    public String convertToDatabaseColumn(AutopayEvery every) {
        return every == null ? null : every.key();
    }

    @Override
    public AutopayEvery convertToEntityAttribute(String key) {
        return key == null ? null : AutopayEvery.fromKey(key).orElseThrow();
    }
}
