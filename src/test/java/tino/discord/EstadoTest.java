package tino.discord;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoTest {

    @Test
    void todoEstadoTemSeuGifNoRepositorio() {
        for (Estado e : Estado.values()) {
            String arquivo = e.url().substring(e.url().indexOf("docs/"));
            assertTrue(Files.exists(Path.of(arquivo)), arquivo);
        }
    }

    @Test
    void registroDeMadrugadaPegaOTinoDormindo() {
        assertEquals(Estado.DORMINDO, Estado.aoRegistrar(LocalTime.of(0, 0)));
        assertEquals(Estado.DORMINDO, Estado.aoRegistrar(LocalTime.of(5, 59)));
        assertEquals(Estado.SUCESSO, Estado.aoRegistrar(LocalTime.of(6, 0)));
        assertEquals(Estado.SUCESSO, Estado.aoRegistrar(LocalTime.of(23, 59)));
    }
}
