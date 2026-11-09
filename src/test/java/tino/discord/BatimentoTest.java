package tino.discord;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatimentoTest {

    @Test
    void semUrlNaoPinga() {
        assertTrue(Batimento.de(null).isEmpty());
        assertTrue(Batimento.de(" ").isEmpty());
    }

    @Test
    void comFalhaSoAvisa() {
        // Porta 1: ninguém escuta. O ping falha sem lançar exceção.
        assertFalse(Batimento.de("http://localhost:1/ping").orElseThrow().pingar());
    }

    @Test
    void pingaComGet() throws IOException {
        AtomicInteger recebidos = new AtomicInteger();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/ping", troca -> {
            if (troca.getRequestMethod().equals("GET")) {
                recebidos.incrementAndGet();
            }
            troca.sendResponseHeaders(200, -1);
            troca.close();
        });
        servidor.start();
        try {
            String url = "http://localhost:" + servidor.getAddress().getPort() + "/ping";

            assertTrue(Batimento.de(url).orElseThrow().pingar());
            assertEquals(1, recebidos.get());
        } finally {
            servidor.stop(0);
        }
    }
}
