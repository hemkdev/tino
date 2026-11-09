package tino.discord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Avisa um serviço externo (healthchecks.io) de que o bot está vivo. Se o ping parar de chegar,
 * é o serviço que manda o alerta: um bot caído não consegue avisar que caiu.
 */
final class Batimento {

    private static final Logger LOG = LoggerFactory.getLogger(Batimento.class);
    static final Duration INTERVALO = Duration.ofMinutes(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI url;

    private Batimento(URI url) {
        this.url = url;
    }

    /** Sem URL (desenvolvimento local), não há batimento. */
    static Optional<Batimento> de(String url) {
        return url == null || url.isBlank() ? Optional.empty() : Optional.of(new Batimento(URI.create(url)));
    }

    /** Um GET. Falha nunca derruba o bot: vira aviso no log, sem a URL, que funciona como senha. */
    boolean pingar() {
        try {
            HttpRequest pedido = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10)).GET().build();
            int status = http.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status / 100 != 2) {
                LOG.warn("batimento recusado: HTTP {}", status);
                return false;
            }
            return true;
        } catch (IOException e) {
            LOG.warn("batimento falhou: {}", e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Pinga a cada {@link #INTERVALO}, só enquanto {@code conectado} disser que o bot está no Discord. */
    void iniciar(BooleanSupplier conectado) {
        Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("batimento").factory())
                .scheduleAtFixedRate(() -> {
                    if (conectado.getAsBoolean()) {
                        pingar();
                    }
                }, 0, INTERVALO.toSeconds(), TimeUnit.SECONDS);
    }
}
