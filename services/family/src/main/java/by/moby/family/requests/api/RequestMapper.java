package by.moby.family.requests.api;

import by.moby.family.requests.ChildRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface RequestMapper {

    @Mapping(target = "ts", source = "createdAt")
    RequestView toView(ChildRequest request);

    List<RequestView> toViews(List<ChildRequest> requests);
}
