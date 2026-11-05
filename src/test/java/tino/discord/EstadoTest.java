package tino.discord;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoTest {

    @Test
    void todoEstadoTemSeuGifNoRepositorio() {
        for (Estado e : Estado.values()) {
            String arquivo = e.url().substring(e.url().indexOf("docs/"));
            assertTrue(Files.exists(Path.of(arquivo)), arquivo);
        }
    }
}
