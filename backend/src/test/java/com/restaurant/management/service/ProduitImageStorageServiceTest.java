package com.restaurant.management.service;

import com.restaurant.management.exception.InvalidImageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProduitImageStorageServiceTest {
    @TempDir Path repertoire;

    @Test
    void accepteUneImageEntierementDecodable() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
        ProduitImageStorageService service = new ProduitImageStorageService(
                repertoire.toString(), 5_242_880, 12_000_000
        );

        var image = service.stocker(new MockMultipartFile("fichier", "test.png", "image/png", png));

        assertThat(image.typeMime()).isEqualTo("image/png");
        assertThat(image.taille()).isEqualTo(png.length);
        assertThat(repertoire.resolve(image.nom())).isRegularFile();
    }

    @Test
    void refuseUnFichierTronqueQuiNeContientQueLaSignaturePng() {
        byte[] signature = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        ProduitImageStorageService service = new ProduitImageStorageService(
                repertoire.toString(), 5_242_880, 12_000_000
        );

        assertThatThrownBy(() -> service.stocker(
                new MockMultipartFile("fichier", "fausse.png", "image/png", signature)
        )).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void refuseUneImageDontLeTypeDeclareNeCorrespondPasAuContenu() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );
        ProduitImageStorageService service = new ProduitImageStorageService(
                repertoire.toString(), 5_242_880, 12_000_000
        );

        assertThatThrownBy(() -> service.stocker(
                new MockMultipartFile("fichier", "fausse.jpg", "image/jpeg", png)
        )).isInstanceOf(InvalidImageException.class);
    }
}
