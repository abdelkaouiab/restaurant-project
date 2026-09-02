package com.restaurant.management.service;

import com.restaurant.management.entity.Commande;
import com.restaurant.management.entity.Facture;
import com.restaurant.management.entity.LigneCommande;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class FacturePdfService {
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font GRAS = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRANCE)
            .withZone(ZoneId.systemDefault());

    public byte[] generer(Facture facture, Commande commande) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PDDocumentInformation informations = new PDDocumentInformation();
            informations.setTitle("Facture " + facture.getNumero());
            informations.setAuthor("Le Restaurant");
            informations.setSubject("Facture de la commande " + commande.getNumero());
            document.setDocumentInformation(informations);

            Contexte contexte = new Contexte(document);
            contexte.nouvellePage(facture, commande);
            contexte.ecrireLignes(facture, commande);
            contexte.fermer();
            document.save(sortie);
            return sortie.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("La facture PDF n'a pas pu être générée.", exception);
        }
    }

    private static final class Contexte {
        private static final float MARGE = 42;
        private static final float BAS_PAGE = 72;
        private final PDDocument document;
        private PDPageContentStream contenu;
        private float y;
        private int numeroPage;

        private Contexte(PDDocument document) {
            this.document = document;
        }

        private void nouvellePage(Facture facture, Commande commande) throws IOException {
            fermer();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contenu = new PDPageContentStream(document, page);
            numeroPage++;
            contenu.setNonStrokingColor(new Color(20, 73, 57));
            contenu.addRect(0, 744, PDRectangle.A4.getWidth(), 98);
            contenu.fill();
            texte(GRAS, 22, MARGE, 793, "LE RESTAURANT");
            texte(REGULAR, 11, MARGE, 771, "Facture " + facture.getNumero());
            texte(REGULAR, 9, 500, 768, "Page " + numeroPage);

            texte(GRAS, 18, MARGE, 710, "FACTURE");
            texte(REGULAR, 10, MARGE, 686, "Numero : " + facture.getNumero());
            texte(REGULAR, 10, MARGE, 670, "Commande : " + commande.getNumero());
            texte(REGULAR, 10, 330, 686, "Emise le : " + DATE.format(facture.getEmiseLe()));
            texte(REGULAR, 10, 330, 670, "Table : " + commande.getTable().getNumero());

            y = 622;
            enteteTableau();
        }

        private void enteteTableau() throws IOException {
            contenu.setNonStrokingColor(new Color(231, 239, 235));
            contenu.addRect(MARGE, y, 511, 24);
            contenu.fill();
            texte(GRAS, 9, MARGE + 8, y + 8, "PRODUIT");
            texte(GRAS, 9, 300, y + 8, "QTE");
            texte(GRAS, 9, 350, y + 8, "PRIX UNIT.");
            texte(GRAS, 9, 455, y + 8, "SOUS-TOTAL");
            y -= 25;
        }

        private void ecrireLignes(Facture facture, Commande commande) throws IOException {
            for (LigneCommande ligne : commande.getLignes()) {
                if (y < BAS_PAGE + 70) {
                    nouvellePage(facture, commande);
                }
                texte(REGULAR, 9, MARGE + 8, y, raccourcir(ligne.getProduit().getNom(), 38));
                texte(REGULAR, 9, 305, y, Integer.toString(ligne.getQuantite()));
                texte(REGULAR, 9, 350, y, montant(ligne.getPrixUnitaire()));
                texte(GRAS, 9, 465, y, montant(ligne.getSousTotal()));
                ligneHorizontale(y - 8);
                y -= 25;
            }

            y -= 16;
            texte(GRAS, 12, 355, y, "TOTAL TTC");
            texte(GRAS, 14, 465, y, montant(facture.getMontantTotal()));
            y -= 50;
            texte(REGULAR, 9, MARGE, y, "Facture generee apres validation du paiement complet.");
            texte(REGULAR, 9, MARGE, y - 15, "Aucune donnee bancaire sensible n'est conservee sur ce document.");
        }

        private void ligneHorizontale(float positionY) throws IOException {
            contenu.setStrokingColor(new Color(220, 220, 215));
            contenu.setLineWidth(0.5f);
            contenu.moveTo(MARGE, positionY);
            contenu.lineTo(553, positionY);
            contenu.stroke();
        }

        private void texte(PDType1Font police, float taille, float x, float positionY, String valeur)
                throws IOException {
            contenu.beginText();
            contenu.setNonStrokingColor(new Color(25, 34, 29));
            if (positionY >= 744) contenu.setNonStrokingColor(Color.WHITE);
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

    private static String montant(BigDecimal valeur) {
        return String.format(Locale.FRANCE, "%.2f EUR", valeur);
    }

    private static String raccourcir(String valeur, int maximum) {
        if (valeur == null) return "";
        String nettoyee = nettoyerTexte(valeur);
        return nettoyee.length() <= maximum
                ? nettoyee
                : nettoyee.substring(0, maximum - 3) + "...";
    }

    private static String nettoyerTexte(String valeur) {
        if (valeur == null) return "";
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
