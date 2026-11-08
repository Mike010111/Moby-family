package by.moby.payment.limits.api;

import by.moby.payment.limits.LimitService.LimitState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface LimitViewMapper {

    @Mapping(target = "id", source = "limit.id")
    @Mapping(target = "count", source = "limit.count")
    @Mapping(target = "unit", expression = "java(state.limit().getUnit().key())")
    @Mapping(target = "amount", source = "limit.amount")
    @Mapping(target = "categories", source = "limit.categories")
    @Mapping(target = "start", source = "limit.startAt")
    LimitView toView(LimitState state);

    List<LimitView> toViews(List<LimitState> states);
}
