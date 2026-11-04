# Decisões

O que foi escolhido, o que foi recusado e por quê. O esquema e as regras do banco estão em
[banco.md](banco.md); as do personagem, em [personagem.md](personagem.md).

## 1. Sem framework e sem ORM

Java 25, JDA, `sqlite-jdbc` e JUnit. O projeto começou com arquitetura hexagonal completa, com
portas, adaptadores e documento de especificação, e foi reduzido de propósito quando ficou claro
que a estrutura era maior que o problema. SQL escrito à mão cabe numa classe (`Financas`) e não
esconde o que cada consulta faz. Os commits dessa fase continuam no histórico.

## 2. O núcleo não conhece o canal

```
tino/nucleo/    Tino (casos de uso), Financas (SQL), Entrada, Dinheiro e os records do domínio
tino/discord/   Bot (eventos do JDA), Visual (embeds e botões), Estado, Pendentes
```

Canal novo (site, Telegram, MCP) fala só com a classe `Tino`. `Financas` é package-private, então
a fronteira não depende de disciplina: chamar SQL de fora de `tino.nucleo` não compila. Cálculo
(projeção do mês, CSV, interpretação de data) mora no núcleo; o canal só formata.

## 3. Um processo, uma conexão, um lock

Discord, site e MCP rodam no mesmo processo e compartilham a instância de `Tino`. Ela tem uma
conexão só, e os métodos são `synchronized`. Um app pessoal não tem concorrência que justifique
pool; se tiver, a troca é por HikariCP sem mexer na API.

## 4. Conta própria, várias identidades

Os dados são da conta do Tino, não do id do Discord. Cada canal resolve a conta com
`Tino.conta(provedor, externo)` na entrada, e entrar pelo Google mais tarde é ligar mais uma
identidade à mesma conta. A migração dos ids antigos do Discord roda no boot e é idempotente.

## 5. Evento selado em vez de flag

`Tino.lancar` devolve um `Evento`: `Registrado` ou `ForaDoNormal`. Quando o valor passa de 5× a
média recente da categoria, nada é gravado e o canal pergunta antes; confirmado, chama
`Tino.registrar`. Por ser `sealed`, o `switch` de cada canal precisa cobrir todos os casos para
compilar, então um evento novo não passa despercebido em nenhum canal. Erro de digitação continua
como `IllegalArgumentException`, que é o idioma do Java para entrada inválida.

O pedido à espera de confirmação fica em memória no canal Discord (`Pendentes`), com chave
aleatória e retirada só pela dona. Reiniciar o bot perde os pendentes, e o botão responde que
expirou.

## 6. A data entra por parâmetro

Métodos que dependem do dia recebem `LocalDate hoje` em vez de chamar `LocalDate.now()`. Mesmo
tamanho de código, e qualquer regra de período fica testável de forma determinística.

## 7. Sem interface de repositório

Existe uma implementação só, e os testes rodam contra SQLite em memória, que abre em
milissegundos. Uma interface aqui só existiria para ser trocada por mock, e não há o que trocar.

## 8. Personagem próprio

O mascote antigo derivava do Wumpus. As diretrizes de marca da Discord proíbem personagens
parecidos, e um mascote da Discord não serve num produto que vai para Telegram e site. O Tino é
uma criatura inventada, desenhada em SVG e animada só com CSS, para sair como SVG, GIF ou PNG sem
JavaScript. Detalhes em [personagem.md](personagem.md).

## Recusado

| Tentativa | Por que saiu |
|---|---|
| Arquitetura hexagonal com portas e adaptadores | estrutura maior que o problema |
| Interface de repositório | uma implementação só, testes já rápidos |
| Gravar e avisar valor fora do normal | o erro de digitação já virava histórico; agora pergunta antes |
| Chave sequencial para o pedido pendente | depois de reiniciar, um botão antigo acertava o pedido de outra pessoa |
| Mascote derivado do Wumpus | diretrizes de marca da Discord |
| Fusão de duas contas com dados | sem regra clara de qual dado vence; vincular a uma conta já usada é recusado |
