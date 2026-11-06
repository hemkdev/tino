package tino.nucleo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DinheiroTest {

    @Test
    void aceitaOsFormatosQueUmBrasileiroDigita() {
        assertEquals(5000, Dinheiro.paraCentavos("50"));
        assertEquals(5000, Dinheiro.paraCentavos("50,00"));
        assertEquals(5000, Dinheiro.paraCentavos("R$ 50"));
        assertEquals(123456, Dinheiro.paraCentavos("1.234,56"));
        assertEquals(123456, Dinheiro.paraCentavos("1234.56"));
    }

    @Test
    void arredondaTerceiraCasaParaCima() {
        assertEquals(5100, Dinheiro.paraCentavos("50,995"));
        assertEquals(5099, Dinheiro.paraCentavos("50,994"));
    }

    @Test
    void rejeitaValorNaoPositivo() {
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("0"));
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("-10"));
    }

    @Test
    void rejeitaLixoEVazio() {
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("abc"));
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos(""));
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos(null));
    }

    @Test
    void rejeitaValorAcimaDoLimite() {
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("1000000,01"));
    }

    @Test
    void pontoSemVirgulaEhMilhar() {
        assertEquals(150000, Dinheiro.paraCentavos("1.500"));
        assertEquals(100000000, Dinheiro.paraCentavos("1.000.000"));
        assertEquals(150, Dinheiro.paraCentavos("1.5"));
        assertEquals(123456, Dinheiro.paraCentavos("1.234.56"));
        assertEquals(150000, Dinheiro.paraCentavos("1500."));
    }

    @Test
    void quatroCasasAposPontoEhErro() {
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("1.5000"));
    }

    @Test
    void valorGiganteViraErroDeValidacao() {
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("99999999999999999999999"));
        assertThrows(IllegalArgumentException.class, () -> Dinheiro.paraCentavos("1e30"));
    }

    @Test
    void formataEmReais() {
        assertEquals("R$ 1.234,56", Dinheiro.formatar(123456));
        assertEquals("R$ 0,50", Dinheiro.formatar(50));
    }

    @Test
    void errosDeEntradaDizemComoAcertar() {
        String e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Dinheiro.paraCentavos("abc")).getMessage();

        org.junit.jupiter.api.Assertions.assertTrue(e.contains("`1.234,56`"), e);
    }
}
