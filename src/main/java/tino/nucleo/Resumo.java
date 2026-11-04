package tino.nucleo;

import java.util.List;

public record Resumo(long ganhos, long despesas, List<Linha> porDespesa, List<Linha> porGanho) {

    public long saldo() {
        return ganhos - despesas;
    }

    public boolean vazio() {
        return ganhos == 0 && despesas == 0;
    }
}
