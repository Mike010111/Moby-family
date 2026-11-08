package by.moby.payment.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
class CatalogController {

    record CategoriesResponse(List<Category> categories, List<Category> hardBlocks) {
    }

    @GetMapping("/catalog/categories")
    CategoriesResponse categories() {
        return new CategoriesResponse(Categories.ALL, Categories.HARD_BLOCKS);
    }

    @GetMapping("/merchants")
    List<Merchant> merchants(@RequestParam Cohort cohort) {
        return DemoCatalog.MERCHANTS.get(cohort);
    }

    @GetMapping("/contacts")
    List<Contact> contacts() {
        return DemoCatalog.CONTACTS;
    }
}
