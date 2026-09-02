package com.restaurant.management.repository;

import com.restaurant.management.entity.Employe;
import com.restaurant.management.entity.JournalActivite;
import com.restaurant.management.entity.Utilisateur;
import com.restaurant.management.enums.ResultatActivite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class JournalActiviteRepositoryTest {

    @Autowired
    private EmployeRepository employeRepository;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private JournalActiviteRepository journalActiviteRepository;

    @Test
    void listeUniquementLesUtilisateursPresentsDansLeJournal() {
        Employe employeAvecActivite = employeRepository.save(new Employe(
                "AUDIT-1", "Amrani", "Sara", "Gérante", LocalDate.now()
        ));
        Utilisateur utilisateurAvecActivite = utilisateurRepository.save(new Utilisateur(
                "sara.amrani@example.com", "test-hash", employeAvecActivite
        ));
        utilisateurAvecActivite.setActif(false);

        Employe employeSansActivite = employeRepository.save(new Employe(
                "AUDIT-2", "Benali", "Youssef", "Caissier", LocalDate.now()
        ));
        utilisateurRepository.save(new Utilisateur(
                "youssef.benali@example.com", "test-hash", employeSansActivite
        ));

        journalActiviteRepository.saveAndFlush(new JournalActivite(
                utilisateurAvecActivite,
                "CONNEXION",
                "AUTHENTIFICATION",
                null,
                ResultatActivite.SUCCES,
                UUID.randomUUID()
        ));

        assertThat(journalActiviteRepository.findUtilisateursDuJournal())
                .singleElement()
                .satisfies(utilisateur -> {
                    assertThat(utilisateur.id()).isEqualTo(utilisateurAvecActivite.getId());
                    assertThat(utilisateur.email()).isEqualTo("sara.amrani@example.com");
                    assertThat(utilisateur.prenom()).isEqualTo("Sara");
                    assertThat(utilisateur.nom()).isEqualTo("Amrani");
                    assertThat(utilisateur.actif()).isFalse();
                });
    }
}
