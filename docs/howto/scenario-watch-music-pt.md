---
layout: default
title: "Ouça Música no Seu Relógio - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-music-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_audio.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Ouça Música no Seu Relógio

> **Nível:** Iniciante &bull; **Tempo:** ~5 minutos &bull; **Dispositivo:** Smartwatch Wear OS (pareado com celular Android)

> **Somente na versão completa** - este guia não está implementado na versão distribuída pelo Google Play. Ele se aplica à versão completa, um download direto do APK em [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-music" dir="/docs/howto/" current="pt" %}

O FastMediaSorter permite navegar e tocar sua coleção de música diretamente do seu smartwatch Wear OS. Você pode transmitir faixas compartilhadas do celular pareado ou tocar arquivos de áudio locais armazenados no relógio, com capa do álbum, modo aleatório, controle de volume pela coroa giratória, reprodução em segundo plano que sobrevive ao sair do app, e um modo de tela apagada que mantém a música tocando com a tela escura.

---

## O Que Você Vai Precisar

- Um smartwatch rodando **Wear OS 2.0** ou mais recente com o FastMedia Wear instalado
- Um celular Android rodando o FastMediaSorter (se for transmitir música do celular)
- Arquivos de música (MP3, FLAC, AAC, OGG) no seu celular ou transferidos para o armazenamento do relógio
- Fones de ouvido Bluetooth ou o alto-falante do relógio para saída de áudio

---

## Passo 1 - Abra o FastMedia Wear no Seu Relógio

1. Abra a lista de apps no seu smartwatch e toque em **FastMedia Wear**.
2. A tela inicial mostra seis seções, sempre nos mesmos lugares:
   - **Recursos**: fontes de rede e opções de sincronização
   - **Celular**: música e mídia compartilhadas do seu celular Android pareado
   - **Local**: arquivos no próprio armazenamento do relógio, incluindo notas de voz gravadas ali
   - **Streams**: canais de TV e rádio ([guia separado](scenario-watch-tv-pt.md))
   - **Apps**: calculadora, monitor de rede, jogo e os outros miniprogramas
   - **Favoritos**: tudo que você marcou

![Tela principal do FastMedia Wear no smartwatch](screenshots/screenshot-wear-music-step1.png)

---

## Passo 2 - Escolha Sua Fonte de Música

1. Para tocar música do celular: toque em **Celular** na tela principal, depois toque em **Áudio**.
2. Para tocar faixas armazenadas diretamente no relógio: toque em **Local** na tela principal, depois toque em **Música**.
3. O FastMedia Wear se conecta à fonte selecionada e carrega seu catálogo de música.

> **Dica:** A tela inicial mantém uma linha dos recursos que você abriu mais recentemente acima das seis seções - uma célula por coluna, então duas em uma grade de duas colunas e três em uma de três colunas. Depois que você tocou algo, ele fica lá para um único toque, e o último canal de stream que você assistiu fica na mesma linha.

---

## Passo 3 - Navegue e Comece a Reprodução

1. Role pelas suas faixas usando o toque ou a coroa giratória.
2. Cada item exibe o título da faixa, a duração e a miniatura da capa do álbum.
3. Toque em **qualquer faixa** para começar a reprodução imediatamente.

![Navegar faixas de áudio no relógio](screenshots/screenshot-wear-music-step3.png)

---

## Passo 4 - Controle a Reprodução e o Volume

Quando uma faixa começa, o **Player de Áudio** em tela cheia se abre:

- **Reproduzir / Pausar**: toque no botão central destacado para pausar ou retomar a reprodução.
- **Pular faixas**: toque em **Anterior** ou **Próxima** para trocar de faixa na sua playlist.
- **Aleatório**: toque no botão **Aleatório** para misturar a ordem das faixas.
- **Buscar na faixa**: arraste a barra de progresso horizontalmente para pular para qualquer posição da música.
- **Volume**: gire a coroa giratória ou o bezel do seu relógio para ajustar o volume suavemente. Um indicador de nível de volume aparece na tela.
- **Favorito**: toque no ícone de estrela para adicionar a faixa aos seus favoritos.

![Player de áudio com controles de reprodução e volume](screenshots/screenshot-wear-music-step4.png)

---

## Passo 5 - Mantenha a Música Tocando

Há duas formas diferentes de continuar ouvindo, e elas respondem a duas perguntas diferentes.

**Saindo do app** - ative **Continuar tocando em segundo plano** nas configurações do relógio. O áudio então continua depois que você minimiza o app ou volta para o mostrador do relógio, com controles na notificação de mídia. Quando você voltar, a tela inicial traz uma linha nomeando o que está tocando: toque nela para voltar à faixa de onde parou, ou toque no botão de parar ao lado dela para encerrar a reprodução sem abrir mais nada. A opção é opcional, e precisa que as notificações estejam permitidas - sem elas o sistema não consegue manter o serviço de reprodução vivo.

**Ficando no player com a tela apagada** - toque no botão **Tela apagada** na parte inferior dos controles do player. A tela fica completamente preta enquanto a música continua tocando, o que economiza bateria em um relógio OLED. Um único toque apenas marca o ponto que você tocou com um pequeno ponto branco, então uma manga roçando no vidro não muda nada; um toque duplo, um pressionar e segurar, ou o próprio botão do relógio traz os controles de volta. Nos mostradores de relógio menores, o botão fica no menu do player em vez de na linha.

![Botão de modo de tela apagada](screenshots/screenshot-wear-music-step5.png)

> Vídeo e apresentações de slides deliberadamente não são cobertos por nenhum dos dois: eles param quando o app sai da tela, porque uma imagem que ninguém pode ver está apenas consumindo bateria.

---

## Pronto! Recursos do Player

- **Capa do Álbum e Fundo de Onda**: Exibe a capa do álbum em tela cheia ou ondas sonoras dinâmicas atrás dos controles.
- **Integração com a Coroa Giratória**: Controle de volume nativo usando o bezel físico ou a coroa do relógio.
- **Reprodução em Segundo Plano**: O áudio sobrevive ao sair do app, com controles na notificação de mídia e uma linha na tela inicial nomeando o que está tocando.
- **Audição com Tela Apagada**: Blecaute instantâneo da tela dentro do player, preservando a reprodução e a duração da bateria.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| A seção Celular diz "Celular não conectado" | Confirme que o Bluetooth está ativado em ambos os dispositivos e que o FastMediaSorter está instalado no seu celular |
| Nenhum arquivo de música aparece em Local | Copie arquivos MP3 ou FLAC para o armazenamento interno do relógio ou use a seção Celular para tocar do celular |
| O áudio para quando você sai do app | Ative **Continuar tocando em segundo plano** nas configurações do relógio, e permita as notificações - o serviço de reprodução precisa delas para continuar vivo |
| A capa do álbum está faltando | Conecte-se ao Wi-Fi para buscar a capa online, ou confirme que seus arquivos de áudio contêm capa ID3 incorporada |

</div>
