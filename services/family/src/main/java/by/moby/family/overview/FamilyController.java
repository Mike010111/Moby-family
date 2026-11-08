package by.moby.family.overview;

import by.moby.family.members.GeoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family")
class FamilyController {

    private final FamilyQueryService family;
    private final GeoService geo;
    private final FamilyViewMapper mapper;

    FamilyController(FamilyQueryService family, GeoService geo, FamilyViewMapper mapper) {
        this.family = family;
        this.geo = geo;
        this.mapper = mapper;
    }

    @GetMapping("/me")
    FamilyView me() {
        return family.me();
    }

    @PostMapping("/children/{childId}/geo/refresh")
    FamilyView.GeoView refreshGeo(@PathVariable String childId) {
        return mapper.geo(geo.refresh(childId).getGeo());
    }
}
