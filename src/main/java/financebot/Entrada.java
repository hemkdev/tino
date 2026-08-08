package financebot;

import java.util.ArrayList;
import java.util.List;

/**
 * Interpreta o que o usuário digita em texto corrido.
 *
 * <p>Formato: {@code [+]valor categoria [- descrição]}, vários separados por
 * vírgula. O {@code +} marca ganho; sem sinal é gasto, que é o caso comum.
 *
 * <pre>
 * 50 mercado
 * 50 alimentação &gt; mercado - pão e leite
 * +3000 salário
 * 50 mercado, 30 uber, 12 café
 * </pre>
 */
final class Entrada {

    private static final String EXEMPLO = "não entendi — tente `50 mercado` ou `+3000 salário`";

    record Item(long centavos, String categoria, String descricao, boolean ganho) {}

    private Entrada() {}

    static List<Item> lista(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(EXEMPLO);
        }
        List<Item> itens = new ArrayList<>();
        for (String parte : texto.split(",")) {
            if (!parte.isBlank()) {
                itens.add(item(parte));
            }
        }
        if (itens.isEmpty()) {
            throw new IllegalArgumentException(EXEMPLO);
        }
        return itens;
    }

    static Item item(String texto) {
        String resto = texto.trim();

        boolean ganho = resto.startsWith("+");
        if (ganho || resto.startsWith("-")) {
            resto = resto.substring(1).trim();
        }

        int espaco = resto.indexOf(' ');
        String valor = espaco < 0 ? resto : resto.substring(0, espaco);
        long centavos = Dinheiro.paraCentavos(valor);   // lança com mensagem própria

        String cauda = espaco < 0 ? "" : resto.substring(espaco + 1).trim();
        String categoria = cauda;
        String descricao = null;

        int traco = cauda.indexOf(" - ");
        if (traco >= 0) {
            categoria = cauda.substring(0, traco).trim();
            descricao = cauda.substring(traco + 3).trim();
        }

        return new Item(centavos,
                categoria.isBlank() ? null : categoria,     // sem categoria: quem chama decide o padrão
                descricao == null || descricao.isBlank() ? null : descricao,
                ganho);
    }

    /** Vale a pena tratar como lançamento? Usado para ignorar conversa solta na DM. */
    static boolean pareceLancamento(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String primeiro = texto.trim().replaceFirst("^[+-]", "").trim().split("\\s+")[0];
        return primeiro.matches("[0-9]+([.,][0-9]+)*");
    }
}
