package by.moby.rewards;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/** Архитектурные правила сервиса (ArchUnit); границы модулей проверяет ModularityTest. */
class ArchitectureTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("by.moby.rewards");
    }

    @Test
    void controllersDoNotTalkToRepositories() {
        noClasses().that().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("контроллер → сервис → репозиторий")
                .check(classes);
    }

    @Test
    void apiNeverExposesEntities() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(new ArchCondition<>("not return JPA entities") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        if (method.getRawReturnType().isAnnotatedWith(Entity.class)) {
                            events.add(SimpleConditionEvent.violated(method, method.getFullName() + " returns an entity"));
                        }
                    }
                })
                .because("DTO отдельно от сущностей")
                .check(classes);
    }

    @Test
    void timeComesOnlyFromTheInjectedClock() {
        noClasses().should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .because("время — только через внедрённый java.time.Clock (Europe/Minsk)")
                .check(classes);
    }

    @Test
    void noFieldInjection() {
        noFields().should().beAnnotatedWith(Autowired.class).because("зависимости — через конструктор").check(classes);
    }

    @Test
    void moneyIsNeverFloatingPoint() {
        DescribedPredicate<JavaClass> geometry = DescribedPredicate.describe("geometry of the GPS radar",
                c -> c.getSimpleName().startsWith("Geo") || c.getSimpleName().equals("Place") || c.getSimpleName().equals("GeoView"));
        fields().that().areDeclaredInClassesThat(DescribedPredicate.not(geometry))
                .should().notHaveRawType(double.class).andShould().notHaveRawType(float.class)
                .andShould().notHaveRawType(Double.class).andShould().notHaveRawType(Float.class)
                .because("деньги — BigDecimal в Money, никаких double")
                .check(classes);
    }

    @Test
    void entitiesDoNotDependOnWebLayer() {
        noClasses().that().areAnnotatedWith(Entity.class)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .check(classes);
    }
}
