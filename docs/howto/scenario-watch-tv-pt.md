---
layout: default
title: "Assista Canais de TV no Seu Smartwatch - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-tv-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_stream.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Assista Canais de TV no Seu Smartwatch

> **Nível:** Iniciante &bull; **Tempo:** ~10 minutos &bull; **Dispositivo:** Smartwatch Wear OS

> **Somente na versão completa** - este guia não está implementado na versão distribuída pelo Google Play. Ele se aplica à versão completa, um download direto do APK em [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-tv" dir="/docs/howto/" current="pt" %}

O FastMedia Wear reproduz canais de TV e rádio ao vivo direto no seu pulso. O relógio abre o stream pelo seu próprio Wi-Fi, então assim que um canal está na lista você pode assisti-lo com o celular em outro cômodo, dentro de uma bolsa, ou completamente desligado.

> **Procurando música armazenada em vez disso?** Veja [Música no Smartwatch](scenario-watch-music-pt.md). Para seus próprios arquivos em um compartilhamento de NAS ou PC, veja [Conectar o Relógio a Compartilhamentos de Rede](scenario-watch-network-pt.md).

---

## O Que Você Vai Precisar

- Um smartwatch rodando **Wear OS 2.0** ou mais recente com o FastMedia Wear instalado
- Uma rede Wi-Fi que o relógio possa acessar, ou um celular pareado para retransmitir a conexão
- Opcional: FastMediaSorter no seu celular Android, se você quiser enviar seus próprios canais para o relógio

---

## Passo 1 - Abra o Streams

1. Abra o **FastMedia Wear** no seu relógio.
2. Na tela inicial, toque em **Streams**.

![Tela inicial do FastMedia Wear com a seção Streams](screenshots/screenshot-wear-tv-step1.png)

A tela inicial mantém as mesmas seis seções nos mesmos lugares, então o Streams está sempre na linha inferior, qualquer que seja o tamanho de grade escolhido. Acima delas fica uma linha dos recursos que você abriu mais recentemente - depois que você assistiu a algo, o canal que você deixou aparece lá para um único toque.

---

## Passo 2 - Preencha a Lista de Canais

Uma instalação nova ainda não tem canais, e a tela avisa isso.

![Tela do Streams vazia com o botão Atualizar catálogo](screenshots/screenshot-wear-tv-step2.png)

Há duas formas de preenchê-la, e elas funcionam juntas:

- **Baixe o catálogo compartilhado.** Toque em **Atualizar catálogo**. O relógio busca o banco de canais publicado em um único arquivo - muitos milhares de canais de TV e rádio com seus tópicos, idiomas e países.
- **Envie canais do seu celular.** Um canal que você mesmo adicionou no FastMediaSorter no celular pode ser enviado com **Enviar para o relógio** a partir da lista de streams do celular. Canais que você fixa no celular também são elevados em direção ao topo da lista do relógio, logo atrás dos que você fixou no próprio relógio, então os dois ou três que você realmente assiste ficam acessíveis sem rolar. Desfixar no celular retira o canal desse grupo novamente, e um canal que o próprio catálogo do relógio não contém é simplesmente ignorado.

Canais enviados do celular sobrevivem a uma atualização de catálogo - a atualização substitui o banco compartilhado e deixa suas próprias linhas intactas.

---

## Passo 3 - Encontre o Canal Que Você Quer

Os três botões no topo da lista permanecem fixos enquanto a lista rola, então nunca saem do alcance.

- **Pesquisar** filtra a lista enquanto você digita.
- **Filtro** restringe por tópico e por idioma. Os nomes são mostrados no seu idioma de interface em vez do inglês bruto do catálogo, com os mais populosos primeiro, com a contagem de canais em cada linha, e os três idiomas do próprio app no topo.
- **Filtro** também lista as coleções selecionadas que vieram com o catálogo - "TV Russa", "Rádio da ex-URSS", "TV Africana" e as demais, as mesmas que o celular mostra. Escolha uma para ver apenas seus canais, ou escolha **Todos** para remover a restrição. Um canal pode pertencer a várias coleções, então a mesma estação aparece em mais de uma. Se o catálogo baixado não trouxer coleções, a entrada simplesmente não é exibida.
- **Ordenar** oferece Mais usados, Nome A-Z, Nome Z-A e Por tipo de mídia. Mais usados é o padrão e sobe com os canais que você realmente começa a assistir no relógio, então a lista aprende seus hábitos sozinha.

Acima da lista, um pequeno contador de duas linhas mostra quantos canais a pesquisa e os filtros atuais deixam, sobre o tamanho do catálogo inteiro.

![Lista de canais com o contador e a barra de ferramentas fixa](screenshots/screenshot-wear-tv-step3.png)

No modo grade, um canal de vídeo mostra uma imagem de prévia antes mesmo de você tê-lo aberto, tirada de um conjunto de prévias para download. Depois da sua primeira visualização, a prévia é substituída por um quadro capturado do próprio canal.

---

## Passo 4 - Assista

1. Toque em um canal. O player de vídeo abre em tela cheia.
2. **Volume:** gire a coroa giratória ou o bezel.
3. **Buscar:** pressione e segure o botão anterior ou próximo. Ambos os botões permanecem na tela mesmo para um único canal.
4. **Quadro:** o botão de modo de quadro alterna entre ajustar a imagem inteira dentro do vidro redondo e cortá-la para preencher a tela. O relógio lembra sua escolha - ela sobrevive a sair do player e reiniciar o app, e a mesma escolha vale para os seus próprios arquivos de vídeo.
5. **Tela apagada:** o menu do player tem uma entrada **Tela apagada**. A tela fica completamente preta - sem relógio, sem controles - enquanto o canal continua tocando, e o relógio não vai adormecer. Um único toque apenas marca o ponto que você tocou com um pequeno ponto branco; um toque duplo, um pressionar e segurar, ou o próprio botão do relógio traz a imagem e os controles de volta exatamente como você os deixou.
6. **Fixar:** a marca no player fixa o canal. Canais fixados são listados primeiro na próxima vez que você abrir o Streams: os que você fixou aqui no relógio vêm na frente, os fixados no celular vêm depois deles, e todo o resto mantém a ordem que a sua classificação escolhida dá. A fixação é vinculada ao endereço do canal, então sobrevive a uma reimportação de catálogo.

> **O vídeo precisa da tela.** A reprodução em segundo plano mantém o **áudio** tocando depois que você sai do app - útil para canais de rádio - mas o vídeo e as apresentações de slides param quando o app sai da tela. Isso é proposital: um vídeo que você não pode ver só consome a bateria.

---

## Passo 5 - Volte em Um Toque

- A **linha recente da tela inicial** lista o último canal que você tocou ao lado dos recursos de rede que você abriu recentemente, com o próprio ícone do canal. Tocar nele reabre o player.
- Um **tile de Stream** pode ser adicionado ao carrossel de tiles do Wear OS e apontado para um canal diretamente do relógio. A partir daí o canal fica a um deslize do mostrador do relógio, sem precisar abrir o app primeiro.
- A **complicação do último recurso** também mostra o canal, então ela pode ficar no mostrador do relógio.

---

## Passo 6 - Quando o Sinal Está Fraco

Streams ao vivo são a coisa mais exigente que um relógio faz com sua rede, então o app é explícito sobre isso:

- Enquanto um stream toca, o relógio pede ao sistema uma rede de banda larga e a libera quando a reprodução termina.
- Se a conexão atual não conseguir suportar o stream, o relógio avisa isso em vez de falhar silenciosamente.
- Se um stream travar sem erro algum - a forma usual de um stream ao vivo morrer - um watchdog o reancora e reprepara até três vezes, mostrando **Reconectando**. Só quando a rede permanece morta é que ele recorre à mensagem de canal indisponível.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| "Nenhum stream disponível" depois de uma instalação nova | Toque em **Atualizar catálogo**, ou envie um canal do celular com **Enviar para o relógio** |
| "Não foi possível atualizar os streams" | O catálogo é um download de vários megabytes. Coloque o relógio no Wi-Fi em vez de uma conexão retransmitida pelo celular, e tente novamente |
| Um canal abre e depois para | A própria fonte pode estar offline. O relógio tenta novamente três vezes antes de desistir - tente outro canal para diferenciar um stream morto de uma rede morta |
| O vídeo para quando você abaixa o pulso | Esperado: apenas o áudio continua depois que o app sai da tela. Para manter um canal tocando com a tela apagada, permaneça no player e use a entrada **Tela apagada** |
| O canal que você fixou no celular não está no topo | As fixações viajam quando o companion do Wear está ativado no app do celular; verifique isso primeiro |
| O som está muito baixo | Gire o bezel ou a coroa no player - isso muda o volume de mídia do relógio, não a posição de reprodução |

</div>
