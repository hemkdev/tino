package tino.discord;

import tino.nucleo.Pedido;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pedidos fora do normal à espera de [Tá certo] ou [Corrigir]. A chave vai no id do
 * botão; quem clica precisa ser a dona do pedido.
 *
 * <p>ponytail: em memória. Reiniciar o bot perde os pedidos (o botão responde que
 * expirou) e um aviso nunca respondido fica até o próximo boot.
 */
final class Pendentes {

    private record Item(String conta, Pedido pedido) {}

    private final Map<String, Item> itens = new ConcurrentHashMap<>();

    /** Chave aleatória: um botão de antes de um reinício nunca acerta um pedido novo. */
    String guardar(String conta, Pedido pedido) {
        String chave = UUID.randomUUID().toString().replace("-", "");
        itens.put(chave, new Item(conta, pedido));
        return chave;
    }

    /** Devolve o pedido uma vez só, e só para a dona; nos outros casos, {@code null}. */
    Pedido retirar(String conta, String chave) {
        Item item = itens.get(chave);
        if (item == null || !item.conta().equals(conta)) {
            return null;
        }
        return itens.remove(chave, item) ? item.pedido() : null;
    }
}
