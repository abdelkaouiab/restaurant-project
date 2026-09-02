package com.restaurant.management.service;

import com.restaurant.management.dto.ConsommationIngredientResponse;
import com.restaurant.management.dto.ConsommationProduitResponse;
import com.restaurant.management.dto.ConsommationResponse;
import com.restaurant.management.enums.UniteMesure;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class ConsommationPdfService {

    private static final PDType1Font REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font GRAS =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withLocale(Locale.FRANCE);

    private final ConsommationService consommationService;

    public ConsommationPdfService(ConsommationService consommationService) {
        this.consommationService = consommationService;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GERANT')")
    public byte[] generer(LocalDate debut, LocalDate fin) {
        ConsommationResponse rapport = consommationService.consulter(debut, fin);
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PDDocumentInformation informations = new PDDocumentInformation();
            informations.setTitle("Rapport de consommation du " + rapport.debut() + " au " + rapport.fin());
            informations.setAuthor("Le Restaurant");
            informations.setSubject("Consommations de produits et d'ingrédients");
            document.setDocumentInformation(informations);

            Contexte contexte = new Contexte(document);
            contexte.nouvellePage();
            contexte.ecrireIntroduction(rapport);
            if (rapport.produits().isEmpty() && rapport.ingredients().isEmpty()) {
                contexte.messageSansDonnees();
            } else {
                contexte.ecrireProduits(rapport);
                contexte.ecrireIngredients(rapport);
            }
            contexte.fermer();
            document.save(sortie);
            return sortie.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Le rapport PDF n'a pas pu être généré.", exception);
        }
    }

    private static final class Contexte {
        private static final float MARGE = 42;
        private static final float BAS_PAGE = 72;
        private static final float LARGEUR = 511;

        private final PDDocument document;
        private PDPageContentStream contenu;
        private float y;
        private int numeroPage;

        private Contexte(PDDocument document) {
            this.document = document;
        }

        private void nouvellePage() throws IOException {
            fermer();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contenu = new PDPageContentStream(document, page);
            numeroPage++;
            contenu.setNonStrokingColor(new Color(20, 73, 57));
            contenu.addRect(0, 760, PDRectangle.A4.getWidth(), 82);
            contenu.fill();
            texte(GRAS, 20, MARGE, 798, "LE RESTAURANT", Color.WHITE);
            texte(REGULAR, 9, 505, 795, "Page " + numeroPage, Color.WHITE);
            y = 724;
        }

        private void ecrireIntroduction(ConsommationResponse rapport) throws IOException {
            texte(GRAS, 18, MARGE, y, "RAPPORT DE CONSOMMATION");
            y -= 30;
            texte(REGULAR, 10, MARGE, y,
                    "Période : du " + DATE.format(rapport.debut()) + " au " + DATE.format(rapport.fin()));
            y -= 17;
            texte(REGULAR, 10, MARGE, y,
                    "Rapport généré le : " + DATE.format(LocalDate.now(ZoneOffset.UTC)));
            y -= 38;
        }

        private void ecrireProduits(ConsommationResponse rapport) throws IOException {
            enteteProduits("PRODUITS CONSOMMÉS");
            for (ConsommationProduitResponse produit : rapport.produits()) {
                if (y < BAS_PAGE + 28) {
                    nouvellePage();
                    enteteProduits("PRODUITS CONSOMMÉS (SUITE)");
                }
                texte(REGULAR, 9, MARGE + 8, y, raccourcir(produit.produit(), 64));
                texte(GRAS, 9, 465, y, Long.toString(produit.quantite()));
                ligneHorizontale(y - 8);
                y -= 24;
            }
            y -= 26;
        }

        private void ecrireIngredients(ConsommationResponse rapport) throws IOException {
            if (y < BAS_PAGE + 75) {
                nouvellePage();
            }
            enteteIngredients("INGRÉDIENTS UTILISÉS");
            for (ConsommationIngredientResponse ingredient : rapport.ingredients()) {
                if (y < BAS_PAGE + 28) {
                    nouvellePage();
                    enteteIngredients("INGRÉDIENTS UTILISÉS (SUITE)");
                }
                texte(REGULAR, 9, MARGE + 8, y, raccourcir(ingredient.ingredient(), 48));
                texte(GRAS, 9, 365, y, quantite(ingredient.quantite()));
                texte(REGULAR, 9, 485, y, unite(ingredient.unite()));
                ligneHorizontale(y - 8);
                y -= 24;
            }
        }

        private void enteteProduits(String titre) throws IOException {
            titreSection(titre);
            fondEntete();
            texte(GRAS, 9, MARGE + 8, y + 8, "PRODUIT");
            texte(GRAS, 9, 435, y + 8, "QUANTITÉ");
            y -= 25;
        }

        private void enteteIngredients(String titre) throws IOException {
            titreSection(titre);
            fondEntete();
            texte(GRAS, 9, MARGE + 8, y + 8, "INGRÉDIENT");
            texte(GRAS, 9, 345, y + 8, "QUANTITÉ UTILISÉE");
            texte(GRAS, 9, 480, y + 8, "UNITÉ");
            y -= 25;
        }

        private void titreSection(String titre) throws IOException {
            texte(GRAS, 13, MARGE, y, titre);
            y -= 30;
        }

        private void fondEntete() throws IOException {
            contenu.setNonStrokingColor(new Color(231, 239, 235));
            contenu.addRect(MARGE, y, LARGEUR, 24);
            contenu.fill();
        }

        private void messageSansDonnees() throws IOException {
            contenu.setNonStrokingColor(new Color(247, 244, 235));
            contenu.addRect(MARGE, y - 38, LARGEUR, 54);
            contenu.fill();
            texte(GRAS, 11, MARGE + 16, y - 8,
                    "Aucune consommation enregistrée pour cette période.");
        }

        private void ligneHorizontale(float positionY) throws IOException {
            contenu.setStrokingColor(new Color(220, 220, 215));
            contenu.setLineWidth(0.5f);
            contenu.moveTo(MARGE, positionY);
            contenu.lineTo(MARGE + LARGEUR, positionY);
            contenu.stroke();
        }

        private void texte(PDType1Font police, float taille, float x, float positionY, String valeur)
                throws IOException {
            texte(police, taille, x, positionY, nettoyerTexte(valeur), new Color(25, 34, 29));
        }

        private void texte(
                PDType1Font police,
                float taille,
                float x,
                float positionY,
                String valeur,
                Color couleur
        ) throws IOException {
            contenu.beginText();
            contenu.setNonStrokingColor(couleur);
            contenu.setFont(police, taille);
            contenu.newLineAtOffset(x, positionY);
            contenu.showText(nettoyerTexte(valeur));
            contenu.endText();
        }

        private void fermer() throws IOException {
            if (contenu != null) {
                contenu.close();
                contenu = null;
            }
        }
    }

    private static String quantite(BigDecimal valeur) {
        return valeur.setScale(3, RoundingMode.UNNECESSARY).toPlainString().replace('.', ',');
    }

    private static String unite(UniteMesure unite) {
        return switch (unite) {
            case GRAMME -> "g";
            case KILOGRAMME -> "kg";
            case MILLILITRE -> "ml";
            case LITRE -> "L";
            case UNITE -> "unité";
        };
    }

    private static String raccourcir(String valeur, int maximum) {
        String nettoyee = nettoyerTexte(valeur);
        return nettoyee.length() <= maximum
                ? nettoyee
                : nettoyee.substring(0, maximum - 3) + "...";
    }

    private static String nettoyerTexte(String valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", "?");
    }
}
