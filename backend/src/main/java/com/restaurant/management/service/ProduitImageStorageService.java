package com.restaurant.management.service;

import com.restaurant.management.entity.Produit;
import com.restaurant.management.exception.InvalidImageException;
import com.restaurant.management.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;

@Service
public class ProduitImageStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp"
    );

    private final Path repertoire;
    private final long tailleMaximale;
    private final long pixelsMaximum;

    public ProduitImageStorageService(
            @Value("${app.product-images.directory:./data/product-images}") String repertoire,
            @Value("${app.product-images.max-size-bytes:5242880}") long tailleMaximale,
            @Value("${app.product-images.max-pixels:12000000}") long pixelsMaximum
    ) {
        this.repertoire = Path.of(repertoire).toAbsolutePath().normalize();
        this.tailleMaximale = tailleMaximale;
        this.pixelsMaximum = pixelsMaximum;
    }

    public ImageStockee stocker(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new InvalidImageException("Sélectionnez une image non vide.");
        }
        if (fichier.getSize() > tailleMaximale) {
            throw new InvalidImageException("L'image ne doit pas dépasser 5 Mo.");
        }

        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException exception) {
            throw new InvalidImageException("L'image n'a pas pu être lue.");
        }

        String typeDetecte = detecterType(contenu);
        String typeDeclare = fichier.getContentType();
        if (!EXTENSIONS.containsKey(typeDetecte) || !typeDetecte.equals(typeDeclare)) {
            throw new InvalidImageException("Le fichier doit être une image PNG, JPEG ou WebP valide.");
        }
        validerStructure(contenu, typeDetecte);

        String nom = UUID.randomUUID() + EXTENSIONS.get(typeDetecte);
        Path destination = cheminSecurise(nom);
        Path temporaire = null;
        try {
            Files.createDirectories(repertoire);
            temporaire = Files.createTempFile(repertoire, "upload-", ".tmp");
            Files.write(temporaire, contenu);
            try {
                Files.move(temporaire, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaire, destination);
            }
            return new ImageStockee(nom, typeDetecte, contenu.length);
        } catch (IOException exception) {
            supprimerSilencieusement(temporaire);
            throw new IllegalStateException("Le stockage de l'image a échoué.", exception);
        }
    }

    public ImageLue lire(Produit produit) {
        if (!produit.possedeImage()) throw new ResourceNotFoundException();
        Path chemin = cheminSecurise(produit.getImageNomFichier());
        if (!Files.isRegularFile(chemin)) throw new ResourceNotFoundException();
        Resource ressource = new FileSystemResource(chemin);
        return new ImageLue(ressource, produit.getImageTypeMime(), produit.getImageTaille());
    }

    public void supprimer(String nom) {
        if (nom != null) supprimerSilencieusement(cheminSecurise(nom));
    }

    private Path cheminSecurise(String nom) {
        if (nom == null || nom.isBlank() || !Path.of(nom).getFileName().toString().equals(nom)) {
            throw new IllegalStateException("Nom de fichier image invalide.");
        }
        Path chemin = repertoire.resolve(nom).normalize();
        if (!chemin.startsWith(repertoire)) {
            throw new IllegalStateException("Chemin de fichier image invalide.");
        }
        return chemin;
    }

    private String detecterType(byte[] contenu) {
        if (contenu.length >= 8
                && (contenu[0] & 0xff) == 0x89 && contenu[1] == 0x50 && contenu[2] == 0x4e
                && contenu[3] == 0x47 && contenu[4] == 0x0d && contenu[5] == 0x0a
                && contenu[6] == 0x1a && contenu[7] == 0x0a) return "image/png";
        if (contenu.length >= 3
                && (contenu[0] & 0xff) == 0xff && (contenu[1] & 0xff) == 0xd8
                && (contenu[2] & 0xff) == 0xff) return "image/jpeg";
        if (contenu.length >= 12
                && contenu[0] == 'R' && contenu[1] == 'I' && contenu[2] == 'F' && contenu[3] == 'F'
                && contenu[8] == 'W' && contenu[9] == 'E' && contenu[10] == 'B' && contenu[11] == 'P') {
            return "image/webp";
        }
        return "";
    }

    private void validerStructure(byte[] contenu, String typeMime) {
        if ("image/webp".equals(typeMime)) {
            validerWebp(contenu);
            return;
        }
        if ("image/png".equals(typeMime) && !terminePar(contenu, new byte[]{0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82})) {
            throw imageInvalide();
        }
        if ("image/jpeg".equals(typeMime) && !terminePar(contenu, new byte[]{(byte) 0xff, (byte) 0xd9})) {
            throw imageInvalide();
        }
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(contenu))) {
            if (flux == null) throw imageInvalide();
            Iterator<ImageReader> lecteurs = ImageIO.getImageReaders(flux);
            if (!lecteurs.hasNext()) throw imageInvalide();
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                String format = lecteur.getFormatName().toLowerCase();
                if (("image/png".equals(typeMime) && !"png".equals(format))
                        || ("image/jpeg".equals(typeMime) && !format.contains("jpeg"))) {
                    throw imageInvalide();
                }
                verifierDimensions(lecteur.getWidth(0), lecteur.getHeight(0));
                BufferedImage image = lecteur.read(0);
                if (image == null) throw imageInvalide();
            } finally {
                lecteur.dispose();
            }
        } catch (InvalidImageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw imageInvalide();
        }
    }

    private void validerWebp(byte[] contenu) {
        if (contenu.length < 30 || lireUInt32LE(contenu, 4) + 8L != contenu.length) throw imageInvalide();
        String bloc = new String(contenu, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int debut = 20;
        int largeur;
        int hauteur;
        if ("VP8X".equals(bloc) && contenu.length >= 30) {
            largeur = 1 + lireUInt24LE(contenu, debut + 4);
            hauteur = 1 + lireUInt24LE(contenu, debut + 7);
        } else if ("VP8L".equals(bloc) && contenu.length >= 25 && (contenu[debut] & 0xff) == 0x2f) {
            int bits = (contenu[debut + 1] & 0xff) | ((contenu[debut + 2] & 0xff) << 8)
                    | ((contenu[debut + 3] & 0xff) << 16) | ((contenu[debut + 4] & 0xff) << 24);
            largeur = 1 + (bits & 0x3fff);
            hauteur = 1 + ((bits >>> 14) & 0x3fff);
        } else if ("VP8 ".equals(bloc) && contenu.length >= 30
                && (contenu[debut + 3] & 0xff) == 0x9d && (contenu[debut + 4] & 0xff) == 0x01
                && (contenu[debut + 5] & 0xff) == 0x2a) {
            largeur = ((contenu[debut + 7] & 0x3f) << 8) | (contenu[debut + 6] & 0xff);
            hauteur = ((contenu[debut + 9] & 0x3f) << 8) | (contenu[debut + 8] & 0xff);
        } else {
            throw imageInvalide();
        }
        verifierDimensions(largeur, hauteur);
    }

    private void verifierDimensions(int largeur, int hauteur) {
        if (largeur <= 0 || hauteur <= 0 || (long) largeur * hauteur > pixelsMaximum) {
            throw new InvalidImageException("Les dimensions de l'image sont invalides ou trop grandes.");
        }
    }

    private long lireUInt32LE(byte[] donnees, int index) {
        return (donnees[index] & 0xffL) | ((donnees[index + 1] & 0xffL) << 8)
                | ((donnees[index + 2] & 0xffL) << 16) | ((donnees[index + 3] & 0xffL) << 24);
    }

    private int lireUInt24LE(byte[] donnees, int index) {
        return (donnees[index] & 0xff) | ((donnees[index + 1] & 0xff) << 8)
                | ((donnees[index + 2] & 0xff) << 16);
    }

    private boolean terminePar(byte[] contenu, byte[] suffixe) {
        if (contenu.length < suffixe.length) return false;
        for (int i = 0; i < suffixe.length; i++) {
            if (contenu[contenu.length - suffixe.length + i] != suffixe[i]) return false;
        }
        return true;
    }

    private InvalidImageException imageInvalide() {
        return new InvalidImageException("Le contenu de l'image est incomplet ou invalide.");
    }

    private void supprimerSilencieusement(Path chemin) {
        if (chemin == null) return;
        try {
            Files.deleteIfExists(chemin);
        } catch (IOException ignored) {
            // Un nettoyage ultérieur peut supprimer ce fichier orphelin sans bloquer l'opération métier.
        }
    }

    public record ImageStockee(String nom, String typeMime, long taille) {}
    public record ImageLue(Resource ressource, String typeMime, long taille) {}
}
