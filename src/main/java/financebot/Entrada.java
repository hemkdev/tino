package financebot;

import java.util.ArrayList;
import java.util.List;

/**
 * Interpreta o que o usuário digita em texto corrido.
 *
 * <p>Formato: {@code [+]valor categoria [data] [descrição]}, com espaço separando
 * os campos e vários lançamentos separados por vírgula. O {@code +} marca ganho;
 * sem sinal é gasto, que é o caso comum.
 *
 * <p>Depois da categoria, o campo é lido como data quando começa com dígito e
 * como descrição caso contrário — por isso a data, se houver, vem antes.
 *
 * <pre>
 * 50 mercado
 * 50 mercado 07/08
 * 50 mercado 07/08 pão e leite
 * 50 mercado pão e leite
 * 50 alimentação &gt; mercado 07/08 feira
 * +3000 salário
 * 50 mercado, 30 uber, 12 café
 * </pre>
 */
final class Entrada {

    private static final String EXEMPLO = "não entendi — tente `50 mercado` ou `+3000 salário`";

    record Item(long centavos, String categoria, String data, String descricao, boolean ganho) {}

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

        String[] campos = resto.split("\\s+");
        long centavos = Dinheiro.paraCentavos(campos[0]);   // lança com mensagem própria

        int i = 1;
        String categoria = null;
        if (i < campos.length) {
            // "Alimentação > Mercado" ocupa três campos; o ">" costura os pedaços.
            StringBuilder caminho = new StringBuilder(campos[i++]);
            while (i < campos.length
                    && (campos[i].startsWith(">") || caminho.charAt(caminho.length() - 1) == '>')) {
                caminho.append(' ').append(campos[i++]);
            }
            categoria = caminho.toString();
        }

        // Começa com dígito, é data; começa com letra, já é a descrição.
        String data = null;
        if (i < campos.length && Character.isDigit(campos[i].charAt(0))) {
            data = campos[i++];
        }

        if (i < campos.length && campos[i].equals("-")) {
            i++;   // "50 mercado - pão" continua funcionando
        }
        String descricao = i < campos.length
                ? String.join(" ", java.util.Arrays.copyOfRange(campos, i, campos.length))
                : null;

        return new Item(centavos,
                categoria == null || categoria.isBlank() ? null : categoria,
                data,
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
