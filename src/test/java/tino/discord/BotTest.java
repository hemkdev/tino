package tino.discord;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotTest {

    @Test
    void mensagemSemSentidoRecebeNaoEntendi() {
        assertEquals(Bot.Dm.NAO_ENTENDI, Bot.tipoDaDm("mercado 50"));
        assertEquals(Bot.Dm.NAO_ENTENDI, Bot.tipoDaDm("oi tudo bem"));
    }

    @Test
    void lancamentoNaoRecebeNaoEntendi() {
        assertEquals(Bot.Dm.LANCAMENTO, Bot.tipoDaDm("50 mercado"));
        assertEquals(Bot.Dm.CONSULTA, Bot.tipoDaDm("quanto gastei essa semana"));
    }

    @Test
    void helpEhApelidoDeAjuda() {
        List<String> nomes = Bot.comandos().stream().map(CommandData::getName).toList();

        assertTrue(nomes.contains("ajuda"));
        assertTrue(nomes.contains("help"));
    }

    @Test
    void codigoDeErroTemQuatroCaracteres() {
        assertTrue(Bot.codigoDeErro().matches("[A-Z0-9]{4}"));
    }

    @Test
    void comandoDigitadoComoTextoViraAjuda() {
        // Sem escolher na lista, o Discord manda "/help" como mensagem comum.
        assertEquals(Bot.Dm.CONSULTA, Bot.tipoDaDm("/help"));
        assertEquals(Bot.Dm.CONSULTA, Bot.tipoDaDm("/ajuda"));
    }
}
