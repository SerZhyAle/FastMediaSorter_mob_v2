---
layout: default
title: "Player de Música no Carro (Rádio Android) - FastMediaSorter v2"
permalink: /docs/howto/scenario-car-music-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 🚗 Player de Música no Carro (Rádio Android)

> **Nível:** Iniciante &bull; **Tempo:** ~10 minutos &bull; **Edição:** Standard, Legacy, VR, noLegal (Lite toca áudio local, mas não tem reprodução em segundo plano nem Streams; Photos não tem áudio)

{% include lang-switcher.html doc="scenario-car-music" dir="/docs/howto/" current="pt" %}

O FastMediaSorter funciona muito bem como player de música no carro em rádios Android - acesso instantâneo a toda a sua coleção de músicas no cartão SD ou pendrive USB, com suporte aos botões do volante já integrado.

> **O que é um rádio Android?** É um som automotivo com tela sensível ao toque que roda Android - como o seu celular, mas instalado no painel. Este guia também funciona em um celular ou tablet comum montado no carro.

---

## O Que Você Vai Precisar

- Rádio Android / celular / tablet no carro
- Arquivos de música no **cartão SD**, **pendrive USB** ou **armazenamento interno** (MP3, FLAC, AAC, OGG e outros)
- (Opcional) Botões de mídia no volante

---

## Passo 1 - Adicione Sua Pasta de Música

1. Abra o app
2. Toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na barra de ferramentas superior
3. Selecione **"Pasta Local"**
4. Navegue até onde sua música está armazenada:
   - **Cartão SD:** procure uma pasta chamada `/storage/` - dentro dela você vai encontrar uma pasta com um código como `1234-5678`, e sua música geralmente está em `/storage/1234-5678/Music`
   - **Armazenamento interno:** tente `/sdcard/Music` ou `/sdcard/Download`
   - **Pendrive USB:** procure em `/storage/usb0/` ou `/storage/usbdisk/`
5. Selecione a pasta → toque em **Selecionar**

> **Não encontra sua música?** Tente tocar em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Pasta Local"** e depois procure por uma pasta chamada `Music` em qualquer lugar da lista. Na maioria dos dispositivos ela está bem ali.


---

## Passo 2 - Configure a Pasta para Música

Toque e segure na sua pasta de música na tela principal → toque em **Editar (ícone de lápis)**.

Isso abre as configurações da pasta. Defina estas opções:

| Configuração | Valor | Por quê |
|---------|-------|-----|
| **Perfil** | Biblioteca de Áudio | Diz ao app "esta é uma pasta de música" - configura tudo automaticamente para áudio |
| **Tipos Suportados** | Somente áudio | Oculta fotos e vídeos para que só as faixas de música apareçam |
| **Modo de ordenação** | Título (A→Z) ou Artista | Mantém suas faixas em uma ordem lógica |
| **Incluir Subpastas** | ATIVADO | Se sua música está organizada em subpastas de artista/álbum, isso encontra todas as faixas |

Toque em **Salvar**.

> **O que faz o "Perfil"?** É um predefinido de um toque que configura a pasta da melhor forma para sua finalidade. Escolher "Biblioteca de Áudio" faz o app mostrar a capa do álbum, ordenar corretamente para música e ocultar arquivos que não são de áudio automaticamente.


---

## Passo 3 - Abra a Pasta e Comece a Reproduzir

1. Toque na sua pasta de música na tela principal
2. Todas as faixas aparecem em uma lista com miniaturas das capas dos álbuns
3. Toque em **qualquer faixa** para começar a tocar

O **player de áudio** em tela cheia se abre com a capa do álbum, barra de progresso e controles de reprodução.

![Player de áudio em tela cheia com capa do álbum (Camel - Dust and Dreams)](screenshots/screenshot-car-step3.png)

---

## Passo 4 - Garanta Que a Música Continue Tocando

Este passo garante que a música continue tocando quando a tela se apaga, você troca de app ou recebe uma notificação de chamada:

1. Vá para **Configurações → aba Mídia**
2. Role até a seção **Áudio**
3. Confirme que **"Suporte a áudio"** está ATIVADO

Pronto. Uma vez ativado isso, o app se registra como um player de música adequado - os controles da tela de bloqueio e o player de mídia na barra de notificações aparecem automaticamente.

![Configurações → Mídia → seção Áudio com opções de reprodução em segundo plano](screenshots/screenshot-car-step4.png)

---

## Passo 5 - Teste os Botões do Volante

Pressione **Próxima** ou **Anterior** no seu volante.

**Eles funcionam automaticamente - sem configuração necessária.** O FastMediaSorter responde a todos os botões de mídia Android padrão.

> **Os botões não funcionam?** Alguns rádios mais antigos enviam sinais não padronizados. Tente ir em **Configurações → Acessibilidade** do Android e procure por uma opção "receptor de botão de mídia". Se isso não ajudar, use as zonas de toque na tela (borda esquerda/direita da tela) - elas funcionam perfeitamente.


---

## Passo 6 - (Opcional) Use "Toda a Música" - Um Só Lugar para Todas as Suas Faixas

Se sua música está espalhada em várias pastas (por exemplo, algumas no cartão SD, outras no armazenamento interno), o recurso virtual **Toda a Música** reúne tudo em um só lugar automaticamente:

1. Na tela principal, procure pelo card **"Toda a Música"** - ele geralmente é criado automaticamente se você tem arquivos de áudio locais
2. Se ele não estiver lá: toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → role até **Recursos Virtuais** → toque em **"Toda a Música"**

Agora todas as suas faixas de todos os locais aparecem juntas em uma única lista.

![Tela principal do FastMediaSorter - card do recurso virtual Toda a Música visível](screenshots/screenshot-car-step6.png)

---

## Passo 7 - (Opcional) Atalho na Tela Inicial para Início com Um Toque

Perfeito para quando você só quer entrar no carro e tocar um botão para começar a música:

1. Toque e segure em um espaço vazio na tela inicial → toque em **Widgets**
2. Encontre **FastMediaSorter** na lista → arraste o widget **"Atalho de Recurso"** para a sua tela inicial
3. Quando solicitado, selecione seu recurso de música
4. Pronto - toque no widget a qualquer momento e a música começa imediatamente

---

## Passo 8 - (Opcional) Adicione Estações de Rádio pela Internet

Se o seu rádio tiver uma conexão de dados móveis ou Wi-Fi ativa, você pode adicionar estações de rádio pela internet diretamente - sem precisar de um app extra:

1. Abra o menu principal (hambúrguer ou suspenso) → toque em **Streams**
2. Toque em **Adicionar (+)** → cole qualquer URL de rádio pela internet (http/https, .m3u8, RTSP) e toque em Salvar
3. Ou toque em **Importar catálogo** para navegar pela lista de estações selecionadas embutida e adicionar estações por gênero ou idioma
4. Toque em uma linha de estação para iniciar a reprodução de áudio inline - o nome da estação e a faixa atual aparecem no minicontrole inferior
5. A lista permanece visível para que você possa trocar de estação sem sair da tela

> **Áudio em segundo plano:** para manter o rádio tocando quando você troca de app, vá em **Configurações → Player → Reprodução de áudio em segundo plano** e ative essa opção.

Observação: o Streams exige uma conexão de rede, e a tela do Streams está ausente nas edições Lite e Photos.

---

## Pronto! Controles do Player

Enquanto a música toca, a tela é o seu painel de controle:

- **20% esquerdos da tela** → Faixa anterior
- **20% direitos da tela** → Próxima faixa
- **60% centrais** → Pausar / Reproduzir / Menu de comandos

![Player de áudio rodando em segundo plano - painel de comandos visível](screenshots/screenshot-car-done.png)

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| Os botões do volante não funcionam | Verifique se o seu rádio envia eventos de tecla de mídia Android padrão. Alguns aparelhos precisam do "receptor de botão de mídia" ativado em Configurações → Acessibilidade |
| A música para quando a tela bloqueia | Ative **"Impedir suspensão"** em Configurações → Geral, ou use os controles de notificação de áudio para retomar. Verifique também se o Suporte a áudio está ATIVADO (Passo 4) |
| Nenhuma capa de álbum é exibida | Ative **"Buscar capas de áudio online"** em Configurações → Mídia → Áudio (requer Wi-Fi). Para capas offline, o app lê a capa incorporada no arquivo MP3/FLAC automaticamente |
| Não consigo encontrar música no cartão SD | Algumas versões do Android restringem o acesso ao cartão SD. Tente adicionar o caminho do cartão SD usando o botão **"Procurar.."** no seletor de pastas, que usa o seletor de arquivos do sistema Android com acesso completo ao cartão SD |
| O áudio engasga ou pula | Feche outros apps rodando em segundo plano. Para arquivos FLAC, confirme que o rádio tem poder de processamento suficiente |
| O rádio pela internet para quando troco de app | Vá em Configurações → Player → Reprodução de áudio em segundo plano e confirme que está ativado |

</div>
