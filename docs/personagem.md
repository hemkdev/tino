# O personagem

<img src="marca/svg/ocioso.svg" width="140" align="right" alt="O Tino respirando">

O Tino é um brotinho que guarda moeda: corpo redondo azul-piscina, um broto de
duas folhas na cabeça e uma moeda no colo. Dinheiro que cresce devagar. É uma
criatura inventada — não é bicho, não é cofrinho, e não deriva de nenhum mascote
existente.

**Sério com o dinheiro, gentil com a pessoa.** O carisma está no rosto e no
movimento; a seriedade está no tom. Ele nunca cobra.

## Estados

Cada estado responde a algo que aconteceu de verdade, não é enfeite. A meta é o
núcleo dizer o que aconteceu (`Evento`) e o canal decidir como mostrar.

| | Estado | Quando | Fala |
|---|---|---|---|
| <img src="marca/svg/ocioso.svg" width="72"> | ocioso | esperando | "Me manda um gasto que eu anoto." |
| <img src="marca/svg/sucesso.svg" width="72"> | sucesso | lançamento gravado | "Registrado: **R$ 132,40** em Mercado." |
| <img src="marca/svg/alerta.svg" width="72"> | alerta | valor muito acima do normal da pessoa | "R$ 4.800 em Moradia? Seu normal é 1.800." [Tá certo] [Corrigir] |
| <img src="marca/svg/erro.svg" width="72"> | erro | não entendeu a mensagem, ou algo falhou | "Não achei o valor. Manda assim: `23,50 uber`." |
| <img src="marca/svg/dormindo.svg" width="72"> | dormindo | lançamento entre 0h e 6h | "Hm… anotado. Vai dormir também." |
| <img src="marca/svg/ouvindo.svg" width="72"> | ouvindo | mensagem de voz | "Pode falar, tô ouvindo." |
| <img src="marca/svg/pensando.svg" width="72"> | pensando | interpretando | "Deixa eu ver isso…" |
| <img src="marca/svg/escrevendo.svg" width="72"> | escrevendo | montando a resposta | (digitando…) |

No Discord, os cinco primeiros já aparecem como miniatura das respostas de
lançamento, dos avisos e das boas-vindas; resumo, listas e confirmações simples
seguem sem ele, para não poluir. Ouvindo, pensando e escrevendo ficam para os
canais que mostram a espera (voz, site).

Hoje só o alerta nasce de um `Evento` do núcleo. Sucesso, dormindo (pelo relógio
do servidor) e erro ainda são decididos no canal Discord; levá-los para o núcleo
é o próximo passo, antes de o site precisar da mesma regra.

## Regra das falas

- **Toda pergunta vem com botões; o que não pede resposta é afirmação.** Pergunta
  sem resposta possível vira cobrança.
- **No alerta, só grava depois da resposta.** Pega o `5000` digitado no lugar de
  `50,00` antes de ele virar histórico.
- **Quem julga o gasto é a pessoa, nunca o bot.**

## Próximos traços

Desenhados, ainda não no código:

| Traço | Regra |
|---|---|
| Planta | o broto cresce com os dias com registro **no total**: broto, brotando, muda, frondosa. Nunca encolhe por gasto |
| Ausência | 5 dias sem registro: triste · 10: se afasta · 15: de costas, espiando. Ao voltar: "Voltou! Senti sua falta." |
| Abraça a moeda | gasto acima de 3× o maior da categoria: "Foi planejado?" [Planejado] [Foi impulso] |
| Esconde a moeda | a pessoa marcou "Foi impulso" ou escreveu `#impulso` |
| Olhada de canto | só em categoria que a pessoa pediu para vigiar, com [Parar de vigiar] |

## Como é feito

SVG desenhado em código, animado só com CSS: cada camada (`<g>`) anima uma coisa
só — respiração, olhar, piscar, broto — e as camadas aninhadas somam os
movimentos. Assim cada estado sai como SVG autocontido, GIF ou PNG, sem
JavaScript. No site, a ideia é usar GSAP para as transições entre estados.
