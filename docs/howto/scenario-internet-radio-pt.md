---
layout: default
title: "Rádio pela Internet e Streams - FastMediaSorter v2"
permalink: /docs/howto/scenario-internet-radio-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 📻 Rádio pela Internet e Streams

> **Nível:** Iniciante - **Tempo:** ~10 minutos - **Edição:** Standard, Legacy, VR, noLegal (o Streams está ausente no Lite e no Photos)

{% include lang-switcher.html doc="scenario-internet-radio" dir="/docs/howto/" current="pt" %}

O FastMediaSorter inclui uma tela dedicada de Streams para fontes de áudio e vídeo pela internet. Adicione qualquer URL de rádio pela internet, importe uma playlist .m3u ou navegue por um catálogo de estações selecionado - sem precisar de um app de rádio separado. Funciona muito bem em rádios automotivos Android, players de áudio, celulares e tablets.

> **Substitui:** TuneIn, app Shoutcast, Online Radio, RadioDroid, streams de rede do VLC, players de IPTV.

---

## O Que Você Vai Precisar

- Dispositivo Android com conexão de rede (dados móveis ou Wi-Fi)
- FastMediaSorter Standard, Legacy, VR ou noLegal (a tela do Streams está ausente no Lite e no Photos)
- Uma URL de stream, um arquivo ou URL de playlist .m3u, ou o catálogo selecionado embutido

---

## Passo 1 - Abra a Tela do Streams

Três formas de chegar lá:
- Menu suspenso da tela principal -> **Streams**
- **Configurações -> Mídia -> Streams** -> toque no botão de atalho do Streams
- Integração de boas-vindas -> linha do Streams (apenas na primeira execução)

> **Não vê o Streams no menu?** Vá em Configurações -> Mídia -> Streams e confirme que "Ativar Streams" está ATIVADO. Ele vem ativado por padrão na maioria dos dispositivos.

---

## Passo 2 - Adicione uma Estação ou Stream

**Opção A - Adicionar uma única URL manualmente:**
1. Toque em **Adicionar (+)** na barra de ferramentas da tela do Streams
2. Cole a URL do stream (rádio http/https, HLS .m3u8, rtsp://..)
3. Dê um nome a ela e toque em **Salvar**

**Opção B - Importar uma playlist .m3u:**
1. Toque em **Importar** -> **De URL**
2. Cole a URL da playlist .m3u e confirme
3. Todas as estações da playlist são adicionadas à sua lista

**Opção C - Navegar pelo catálogo selecionado:**
1. Toque em **Importar catálogo** (ou baixe-o na tela de Extensões)
2. Navegue ou pesquise por nome, tópico ou idioma
3. Toque nas estações para adicioná-las à sua lista

---

## Passo 3 - Toque uma Estação

- **Stream de áudio (rádio):** toque na linha - a reprodução começa inline. Um minicontrole fixo aparece na parte inferior mostrando o nome da estação e as informações ICY da faixa tocando no momento. A lista permanece totalmente interativa.
- **Stream de vídeo ou RTSP:** toque na linha - abre no player em tela cheia. Pressione Voltar para retornar à lista; a posição de rolagem e a última estação selecionada são preservadas.

---

## Passo 4 - Mantenha o Rádio Tocando em Segundo Plano

Para manter o áudio tocando quando você troca de app ou bloqueia a tela:

1. Vá para **Configurações -> Mídia -> Player**
2. Encontre o grupo **Reprodução de áudio em segundo plano**
3. Ative **Reprodução de áudio em segundo plano**

> **Saindo da tela do Streams enquanto uma estação está tocando:** o app oferece a mesma escolha de Parar / Continuar tocando que o player principal. Se a reprodução em segundo plano estiver DESATIVADA, o stream para quando você minimiza a tela.

---

## Passo 5 - Filtre e Organize

- **Fixar favoritos no topo:** toque e segure na linha de uma estação -> Fixar. As estações fixadas aparecem acima do resto, independente da ordem de classificação.
- **Filtrar por categoria ou idioma:** toque no botão Filtro (um ponto aparece quando um filtro está ativo). O seletor de idioma mostra bandeiras. Use a alternância E/OU para corresponder a todos ou a qualquer filtro selecionado.
- **Ordenar:** toque no botão de ordenação para ordenar por nome, tópico, idioma ou reproduzido recentemente.
- **Pesquisar:** digite na barra de pesquisa para filtrar por nome em todas as estações.

---

## Passo 6 - O Que Fazer Se Uma Estação Estiver Fora do Ar

Se um stream estiver indisponível ou redirecionado, uma caixa de diálogo aparece com três opções:
- **Tentar novamente** - tenta o stream de novo
- **Remover** - exclui-o da sua lista
- **Cancelar** - descarta e mantém a entrada

---

## Solução de Problemas

| Problema | O que tentar |
|---------|-------------|
| O stream não toca | Verifique se a URL está correta e a estação está online. Tente Tentar novamente na caixa de diálogo de indisponibilidade |
| O áudio para ao trocar de app | Ative a Reprodução de áudio em segundo plano em Configurações -> Mídia -> Player |
| Nenhuma entrada de Streams no menu | A tela do Streams está ausente nas edições Lite e Photos. Use Standard, Legacy, VR ou noLegal |
| A importação do catálogo trava | O servidor do catálogo pode estar lento ou offline. A importação expira automaticamente e mostra um erro - verifique sua conexão e tente novamente |
| Nenhuma bandeira aparece no filtro de idioma | As bandeiras são mostradas com base na tag de idioma no catálogo de estações. Estações adicionadas manualmente sem tag de idioma ficam sempre visíveis em qualquer filtro de idioma |

</div>
