package by.moby.rewards.artifacts;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class SlotConverter implements AttributeConverter<Slot, String> {

    @Override
    public String convertToDatabaseColumn(Slot slot) {
        return slot == null ? null : slot.key();
    }

    @Override
    public Slot convertToEntityAttribute(String key) {
        return key == null ? null : Slot.fromKey(key);
    }
}
