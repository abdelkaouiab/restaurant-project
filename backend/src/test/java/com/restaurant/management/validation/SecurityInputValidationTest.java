package com.restaurant.management.validation;

import com.restaurant.management.controller.AchatController;
import com.restaurant.management.controller.TableauBordController;
import com.restaurant.management.dto.AjouterLigneAchatRequest;
import com.restaurant.management.dto.CreerAchatRequest;
import com.restaurant.management.dto.LigneReceptionAchatRequest;
import com.restaurant.management.enums.ResultatActivite;
import com.restaurant.management.enums.StatutAchat;
import com.restaurant.management.service.AchatService;
import com.restaurant.management.service.ConsultationJournalService;
import com.restaurant.management.service.TableauBordService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityInputValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void preparerValidateur() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void fermerValidateur() {
        validatorFactory.close();
    }

    @Test
    void refuseLesIdentifiantsMetierNonPositifs() {
        assertThat(validator.validate(new CreerAchatRequest(0L))).isNotEmpty();
        assertThat(validator.validate(new AjouterLigneAchatRequest(
                0L, BigDecimal.ONE, BigDecimal.ONE
        ))).isNotEmpty();
        assertThat(validator.validate(new LigneReceptionAchatRequest(0L, BigDecimal.ONE))).isNotEmpty();
    }

    @Test
    void refuseLesParametresDeRechercheHorsBornes() throws Exception {
        AchatController achatController = new AchatController(mock(AchatService.class));
        Method rechercherAchats = AchatController.class.getMethod(
                "rechercher", String.class, StatutAchat.class, int.class, int.class, String.class, String.class
        );

        assertThat(validator.forExecutables().validateParameters(
                achatController,
                rechercherAchats,
                new Object[]{"x".repeat(101), null, -1, 101, "creeLe", "desc"}
        )).hasSize(3);

        TableauBordController tableauBordController = new TableauBordController(
                mock(TableauBordService.class), mock(ConsultationJournalService.class)
        );
        Method rechercherOperations = TableauBordController.class.getMethod(
                "journal",
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                String.class,
                String.class,
                ResultatActivite.class,
                Long.class,
                int.class,
                int.class,
                String.class,
                String.class
        );

        assertThat(validator.forExecutables().validateParameters(
                tableauBordController,
                rechercherOperations,
                new Object[]{null, null, "x".repeat(101), "y".repeat(101), null, 0L, -1, 101, "creeLe", "desc"}
        )).hasSize(5);
    }
}
