package by.moby.payment.limits;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class LimitUnitConverter implements AttributeConverter<LimitUnit, String> {

    @Override
    public String convertToDatabaseColumn(LimitUnit unit) {
        return unit == null ? null : unit.key();
    }

    @Override
    public LimitUnit convertToEntityAttribute(String key) {
        return key == null ? null : LimitUnit.fromKey(key).orElseThrow();
    }
}
