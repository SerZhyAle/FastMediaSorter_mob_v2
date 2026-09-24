---
layout: default
title: "FastMediaSorter v2"
permalink: /docs/README-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# FastMediaSorter v2 🚀

![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![License](https://img.shields.io/badge/License-Apache_2.0-blue?style=flat-square&logo=apache)

{% include lang-switcher.html doc="README" dir="/docs/" current="pt" %}

**📦 Baixar:** [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="56">](https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter)

Instalando o APK diretamente? O Android avisa sobre um pacote que nunca viu antes - [por que o aviso aparece e o que tocar](INSTALL_TRUST.md).

**📘 Documentação do usuário:** [guias passo a passo para cada funcionalidade, com busca](https://serzhyale.github.io/FastMediaSorter_mob_v2/documentation/)

## Sobre o Projeto

**FastMediaSorter v2** é um shell completo para um dispositivo Android. Ele assume a tela inicial, reproduz suas mídias, abre transmissões ao vivo, inicia seus aplicativos, conversa com seu relógio, monitora o dispositivo e gerencia todos os arquivos que você possui - em pastas locais, em unidades de rede (SMB, SFTP, FTP) e em armazenamento na nuvem (Google Drive, OneDrive, Dropbox).

Ele é construído sobre oito pilares: shell de dispositivo, player de mídia, transmissões ao vivo, inicialização de aplicativos, um substituto para os aplicativos padrão, um companion no relógio, monitoramento do dispositivo e um gerenciador de arquivos completo. Organizar arquivos entre todas essas fontes foi onde o aplicativo começou, e ainda é a base sobre a qual o restante foi construído - mas não é mais tudo o que ele faz.

Este manual agora segue o mesmo vocabulário público do inventário canônico de funcionalidades em [FEATURES.md](FEATURES.md) e do mapa de documentação em [DOCS_MAP.md](DOCS_MAP.md). Use essas duas páginas como a fonte de verdade atual para a história do app, edições disponíveis e o conjunto de funcionalidades atual.

## Versão para Windows 🖥️

Procurando uma solução para desktop? Confira o **Fast Media Sorter for Windows** (anteriormente FastMediaSorter LITE) - um aplicativo leve em Windows Forms para organizar, visualizar e gerenciar rapidamente arquivos de imagem e vídeo:

🔗 **[Fast Media Sorter for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

📄 [Como publicar pastas do PC no Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html) - compartilhe as pastas do seu PC com o app via SFTP (importação por companion / leitura de QR no telefone).

Recursos incluem:

- Navegação rápida por pastas grandes de imagens e vídeos
- Modos de slideshow e visualização aleatória de arquivos
- Rastreamento de arquivos e pastas recentes
- Operações de arquivo: mover, copiar, renomear e excluir
- Painel de imagens para navegação visual rápida
- Atalhos de teclado personalizáveis para um fluxo de trabalho eficiente
- Suporte a múltiplos idiomas (Inglês/Russo)
- Compatível com Windows 7/10/11 com .NET Framework 4.8

## Índice

- [Baixar](#download-)
- [Edições](#editions-)
- [Principais Funcionalidades](#key-features)
- [Formatos de Mídia Suportados](#supported-media-formats-)
- [Capturas de Tela](#screenshots-)
- [Cenários de Uso](#usage-scenarios-)
- [Documentação](#documentation-)
- [Companion Wear OS](#wear-os-companion-)
- [Instruções de Build](#build-instructions)
- [Testes](#testing-)
- [Primeiros Passos](#first-steps-quick-usage-guide-)
- [Stack Tecnológico](#technology-stack)

## Edições 🎯 {#editions-}

O FastMediaSorter v2 é lançado em **sete edições** - cinco para celulares e tablets do dia a dia (Standard, Lite, Photos, Legacy, FOSS) mais duas versões para headset e sideload, VR e noLegal. A grade canônica de recursos é gerada a partir do build em [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md):

| Edição | Descrição | Notas |
|--------|-------------|-------|
| **Standard** | Versão completa | Conjunto de recursos mais amplo para mídia, documentos, OCR, tradução e acesso à nuvem |
| **Lite** | Versão leve | Somente arquivos locais - vídeo, áudio e imagens; sem fontes de rede, nuvem, documentos ou Streams |
| **Photos** | Versão focada em fotos | Somente imagens, com SMB/FTP/SFTP e nuvem; sem vídeo e sem áudio |
| **Legacy** | Versão focada em compatibilidade | Mesmo conjunto de recursos do Standard, incluindo SMB/FTP/SFTP e nuvem (Google Drive, Dropbox, OneDrive); feita para Android 6/7 (API 23+) |
| **FOSS** | Versão do catálogo F-Droid | Sem SDKs proprietários: mídia local, documentos, EPUB e SMB/FTP/SFTP; sem nuvem, sem Streams, sem OCR, sem tradução e sem companion do Wear OS |
| **VR** | Versão para headset limpa para lojas | Conjunto completo de mídia para headsets; sem Google Cast e sem companion do Wear OS |
| **noLegal** | Versão para sideload | Tudo do Standard mais o player imersivo OpenXR e extras exclusivos de sideload |

### Qual Edição Devo Baixar?

- **Standard** ⭐ **(Recomendado)**: Melhor escolha padrão para a maioria dos usuários
- **Lite**: Prefira esta opção se quiser um pacote mais leve e uma configuração mais simples
- **Photos**: Prefira esta opção para fluxos de trabalho focados em fotos
- **Legacy**: Escolha esta opção para dispositivos Android 6/7 (API 23+) - inclui rede e nuvem
- **FOSS**: Escolha esta opção no catálogo F-Droid quando quiser uma versão livre de SDKs proprietários
- **VR**: Escolha esta opção para um headset XR - a versão de loja sem Cast e sem suporte ao Wear
- **noLegal**: Somente sideload - escolha quando precisar do player imersivo OpenXR

Para a disponibilidade exata de recursos por edição, use a documentação canônica:

- [Inventário de Funcionalidades (canônico)](FEATURES.md)
- [Como Fazer (tabela de disponibilidade de recursos)](HOW_TO-pt.md)
- [Início Rápido (seletor de edição)](QUICK_START-pt.md)
- [Limitações do Programa](LIMITATIONS.md)

> 🧭 **Primeira execução:** logo abaixo do seletor de idioma, o app permite que você escolha um **perfil de dispositivo** (celular, tablet, TV, carro, porta-retrato, VR e mais) que ajusta os padrões iniciais para você - alterável a qualquer momento nas Configurações. Veja [Primeira Execução: Escolha Seu Perfil de Dispositivo](QUICK_START-pt.md#first-launch-choose-your-device-profile-30-seconds-).

## Baixar 📥 {#download-}

📲 **[Consiga na Google Play](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)**

**Os arquivos APK compilados NÃO são armazenados neste repositório do GitHub.** Todas as versões estão disponíveis no **Google Drive**:

🔗 **[Baixar Todas as Versões do Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| Edição | Nome do Arquivo | Descrição |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | Recursos completos (Nuvem, OCR, EPUB, Tradução) |
| **Lite** | `FastMediaSorter_lite_release.zip` | Somente mídia local (Vídeos, Áudio, Imagens; sem rede, nuvem, documentos ou Streams) |
| **Photos** | `FastMediaSorter_photos_release.zip` | Somente imagens, com rede (SMB/FTP/SFTP) e nuvem |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | Mesmos recursos do Standard, incluindo rede (SMB/FTP/SFTP) e nuvem; Android 6/7 (API 23+) |

> **Observação**: Todas as versões são enviadas automaticamente ao Google Drive após a compilação bem-sucedida.
>
> 🔐 **Senha do ZIP: `1`** (os arquivos APK são compactados em arquivos ZIP protegidos por senha para contornar as restrições do Google Drive)

## Capturas de Tela 📱 {#screenshots-}

| Tela Principal | Ações de Arquivo | Configurações |
|:-----------:|:------------:|:--------:|
| <a href="images/Screenshot_20251109_000251.png"><img src="images/Screenshot_20251109_000251.png" width="200"></a> | <a href="images/Screenshot_20251109_000314.png"><img src="images/Screenshot_20251109_000314.png" width="200"></a> | <a href="images/Screenshot_20251109_000323.png"><img src="images/Screenshot_20251109_000323.png" width="200"></a> |
| **Visualização do Player** | | |
| <a href="images/Screenshot_20251114_184930.png"><img src="images/Screenshot_20251114_184930.png" width="200"></a> | | |

Imagens em tamanho completo:

- [Tela Principal](images/Screenshot_20251109_000251.png)
- [Ações de Arquivo](images/Screenshot_20251109_000314.png)
- [Configurações](images/Screenshot_20251109_000323.png)
- [Visualização do Player](images/Screenshot_20251114_184930.png)

## Principais Funcionalidades {#key-features}

- 🗂️ **Interface Unificada:** Veja e gerencie arquivos de todas as fontes em uma única janela.
- ⚡ **Organização Rápida:** Copie ou mova arquivos para pastas de destino pré-configuradas com um clique.
- ⭐ **Sistema de Favoritos:** Marque arquivos importantes como favoritos e acesse-os rapidamente em uma aba dedicada que reúne favoritos de todas as fontes.
- 🔒 **Proteção por PIN:** Proteja recursos individuais com códigos PIN de acesso para impedir navegação e edição não autorizadas.
- ⚙️ **Configuração por Recurso:** Personalize o intervalo do slideshow, a profundidade de varredura (subpastas) e a geração de miniaturas para cada pasta individualmente.
- 🧭 **Configuração de Perfil de Dispositivo:** Escolha um perfil na primeira execução para celulares, tablets, TVs/media boxes, centrais multimídia de carro, players de mídia, porta-retratos, players de áudio, leitores de e-books, headsets VR ou padrões personalizados; o app aplica padrões correspondentes de segurança, tela, conteúdo e prioridade de comandos.
- 📋 **Recursos Inteligentes Predefinidos:** Recursos virtuais integrados - **Toda a Música**, **Todos os Vídeos**, **Todas as Fotos** - que reúnem mídia de todo o dispositivo sem qualquer configuração. Acesse instantaneamente toda a sua biblioteca de mídia sem adicionar pastas individuais manualmente.
- 🖥️ **Suporte a Rede e Nuvem:** Trabalhe com arquivos em suas unidades de rede (SMB com varredura automática de rede), servidores SFTP, FTP e em armazenamento na nuvem (Google Drive, Dropbox, OneDrive).
- 🖼️ **Visualização Flexível:** Exiba arquivos em uma grade personalizável ou em lista detalhada, com suporte a paginação para coleções grandes (mais de 1000 arquivos).
- ▶️ **Player Integrado:** Reprodução de vídeo e áudio, visualização de imagens e GIFs sem sair do app. Compatível com slideshow e zoom em tela cheia.
- 🧩 **Integração como Player Padrão:** Opções de reprodução permitem que o FastMediaSorter atue como manipulador de mídia do sistema para intents de abertura/compartilhamento (ACTION_VIEW / ACTION_SEND), além de encaminhar eventos de ativação dos botões físicos de mídia para o serviço de reprodução de áudio.
- 🗣️ **AppFunctions do Assistente (Android 16+):** O app declara ações que podem ser chamadas pelo assistente - buscar sua mídia, abrir um arquivo ou abrir uma pasta do computador - para que o assistente do sistema do seu dispositivo encontre e abra seu conteúdo apenas com um pedido.
- 🎛️ **Suporte a Botões Físicos:** Controles do volante, botões de fone de ouvido e teclas físicas de mídia (Reproduzir/Pausar, Próxima, Anterior) são totalmente suportados pelo serviço de áudio em segundo plano - sem necessidade de interação com a tela.
- 📻 **Transmissões de Internet (tela Streams):** Reproduza rádio pela internet (http/https, Icecast/Shoutcast com informações ICY de reprodução atual), transmissões HLS/DASH e fontes RTSP diretamente de uma tela dedicada de Streams. Adicione URLs manualmente, importe uma playlist `.m3u` ou baixe um catálogo selecionado do FastMediaSorter. Fixe favoritos no topo; filtre por categoria e idioma. Áudio embutido: o rádio toca a partir da lista por meio de um mini-controle fixo na parte inferior, enquanto a lista permanece rolável. Vídeo e RTSP abrem no player em tela cheia. Disponível em Standard, Legacy, VR e noLegal; ausente em Lite e Photos.
- 🎵 **Suporte a Letras de Músicas:** Veja a letra da música que está tocando no momento. A busca é feita automaticamente por metadados (Artista/Título) usando `api.lyrics.ovh`, com alternativa por análise do nome do arquivo.
- 🎶 **Música de Fundo no Slideshow:** Toque música de fundo durante slideshows de imagens. Selecione qualquer recurso de áudio como fonte de música, com reprodução aleatória de faixas, controle de volume e exibição do nome da faixa. Toque no nome da faixa para pular para outra faixa aleatória. Funciona perfeitamente com arquivos de rede e da nuvem.
- ✏️ **Edição de Imagens:** Gire, inverta, aplique filtros (escala de cinza, sépia, negativo), ajuste brilho/contraste/saturação - tanto para arquivos locais quanto de rede.
- 🗂️ **Suporte a Arquivos Binários:** Veja e gerencie arquivos binários (ZIP, RAR, APK, ISO, EXE, DLL etc.) com miniaturas geradas mostrando as extensões dos arquivos. Menu de contexto com Compartilhar/Abrir Com/Copiar/Mover/Renomear/Excluir. Disponível apenas no modo "Todos os Arquivos".
- ⌨️ **Teclado, Mouse e Controle de Jogo:** Suporte completo a teclado, mouse e controle de jogo em todas as telas - Navegação, Player, Configurações, diálogos. Totalmente remapeável em Configurações → Gerenciamento → Controles e Atalhos de Teclado; pressione F1 em qualquer tela para ver uma sobreposição de ajuda específica daquela tela. Navegação de listas por D-pad; menu de contexto com clique direito e efeitos de hover para mouse.
- 🔍 **Ordenação e Filtragem:** Ordene arquivos por nome, data, tamanho e duração. Aplique filtros para busca rápida. Suporte a arquivos ocultos (que começam com `.`) com opção dedicada.
- ↩️ **Desfazer e Lixeira:** Capacidade de desfazer a última ação (copiar, mover, excluir) com exclusão reversível para a pasta `.trash/`. Inclui a função "Esvaziar Lixeira" para os recursos.
- 🎨 **Interface Moderna:** Suporte a temas claro e escuro, controles intuitivos, Material Design 3.
- 💾 **Cache Inteligente:** Carregamento de metadados de vídeo em dois estágios (1 MB inicial, 5 MB estendido) e cache de miniaturas configurável (2 GB por padrão, até 16 GB).
- 📄 **Visualizador de Documentos:** Visualizador integrado para arquivos de texto (.txt, .md, .log, .json, .xml) e documentos PDF, com zoom, panorâmica e navegação por gestos.
- 📚 **Leitor de E-books EPUB:** Leitor de EPUB nativo com navegação por capítulos, sumário, controle de tamanho de fonte, busca dentro do livro e suporte a tema claro/escuro. Funciona com arquivos locais e de rede.
- 📥 **Baixar e Abrir:** Baixe arquivos de rede (SMB/SFTP/FTP) para o armazenamento local e abra-os em apps externos com acompanhamento de progresso.
- 🌐 **Tradução Automática:** Traduza instantaneamente o texto de imagens, PDFs e arquivos de texto totalmente no dispositivo: o **Tesseract** lê o texto em alfabetos latino e cirílico, e o Google ML Kit o traduz. Compatível com o modo padrão e com o **modo de sobreposição estilo lente** para traduções no próprio local do texto.
- 📱 **Suporte a Widgets:** Mais de uma dezena de widgets de tela inicial cobrindo uma ampla variedade - atalhos de recursos, players de mídia, captura pela câmera, calculadoras, tarefas agendadas, favoritos, minijogos e muito mais. Veja a seleção completa no seletor de widgets do seu launcher.
- 🏠 **Modo Tela Inicial:** Deixe o app ser a tela inicial do seu dispositivo (versões Standard e noLegal): uma área de trabalho própria com atalhos de recursos que abrem direto em navegação, slideshow ou reprodução, gadgets redimensionáveis como relógio e previsão do tempo, células de contato que não exigem permissão de contatos, uma grade de apps e uma barra de tarefas. Desative a qualquer momento e o Android restaura sua tela inicial anterior.
- ⏰ **Operações de Arquivo Agendadas:** Automatize operações de arquivo (Copiar/Mover/Excluir) usando regras baseadas em horário, com filtros flexíveis e execução em segundo plano.
- 👆 **Gestos Avançados:** Controles inteligentes de zoom (2x/3x/4x) para imagens e zonas de toque intuitivas para navegação entre arquivos.
- 📸 **Salvar Quadro:** Capture o quadro atual do vídeo como um instantâneo PNG ou JPG e salve-o em qualquer recurso configurado - local ou de rede. O formato de saída e o recurso de destino são definidos nas Configurações de Vídeo.
- 🖨️ **Imprimir:** Envie documentos (PDF, TXT) e imagens para uma impressora diretamente do player integrado. Arquivos de rede e da nuvem são armazenados em cache local antes da impressão.
- ⬇️ **Descarregamento de Transmissão:** Baixe um arquivo de rede para o cache local com uma caixa de progresso em tempo real antes ou durante a reprodução. Um aviso opcional de limpeza recupera o espaço de armazenamento depois.
- 🔊 **Áudio DTS/DTS-HD:** Faixas de áudio DTS e DTS-HD são decodificadas por software por meio de uma versão personalizada do FFmpeg - sem necessidade de hardware especial.
- 🎨 **Cor e Brilho do Vídeo:** Ajuste Matiz e Brilho em tempo real usando efeitos de GPU do Media3. As configurações permanecem entre arquivos de vídeo durante a sessão.
- 📤 **Compartilhar com o FastMediaSorter:** Receba arquivos de qualquer app pela folha de compartilhamento padrão do Android e copie-os para um recurso selecionado com um único toque.
- 📷 **Captura de Câmera na Navegação:** Tire uma foto com a câmera do dispositivo e salve-a diretamente no recurso atual - local ou de rede - sem sair do app.
- 🔗 **Download Automático por Link:** Compartilhe qualquer URL http(s) com o app pela folha de compartilhamento do Android; o arquivo de mídia é baixado e salvo automaticamente em um recurso selecionado.
- 👁️ **Modo 3D de Olho Único:** Recorte conteúdo estéreo (SBS/OU) para um único olho, para uma visualização confortável em telas planas; funciona tanto para vídeo quanto para imagens.
- 📲 **Captura e Gravação de Tela:** Faixa de gesto na borda esquerda para capturas de tela, fotos rápidas, recorte e compartilhamento, além de gravação de tela/voz/vídeo sem sair do arquivo atual.
- 📊 **Estatísticas de Uso (opcional):** Painel local com arquivos organizados, espaço liberado e tempo de reprodução - nada sai do dispositivo, a menos que você exporte.
- 🧹 **Localizador de Duplicatas e Limpeza por Tamanho:** Varredura de duplicatas baseada em conteúdo (tamanho, hash rápido, SHA-256) com exclusão manual ou automática, além de uma limpeza por tamanho.

## Formatos de Mídia Suportados 🎞️ {#supported-media-formats-}

O FastMediaSorter v2 é compatível com uma ampla variedade de formatos:

- **Imagens:** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF
- **Vídeo:** MP4, MKV, MOV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG
- **Áudio:** MP3, FLAC, AAC, OGG, M4A, WMA, OPUS, DTS, DTS-HD
- **Documentos:** TXT, MD, LOG, JSON, XML, PDF, **EPUB**
- **Arquivos Binários** (modo Todos os Arquivos): ZIP, RAR, 7z, TAR, GZ, ISO, DMG, IMG, APK, EXE, DLL, SO e mais de 60 outros formatos

## Cenários de Uso 💡 {#usage-scenarios-}

Aqui estão algumas maneiras pelas quais o FastMediaSorter v2 pode ajudar você:

### 1. 📸 Organizando Fotos da Câmera

Conecte seu telefone ou abra uma pasta local da câmera. Configure uma pasta de destino "Melhores Fotos". Abra o visualizador, deslize rapidamente por milhares de fotos e toque no botão de destino para copiar instantaneamente as melhores capturas.

### 2. 🏠 Backup de Rede (NAS)

Adicione seu NAS doméstico via SMB. Navegue pelos seus arquivos de mídia locais. Selecione vários arquivos ou um intervalo e "Mova-os" para o seu NAS para guardá-los com segurança, liberando espaço no seu dispositivo.

### 3. ☁️ Gerenciamento na Nuvem

Conecte sua conta do Google Drive, Dropbox ou OneDrive. Navegue pelos seus arquivos na nuvem sem precisar baixar tudo. Exclua arquivos indesejados ou organize-os em pastas diretamente na nuvem.

### 4. 📺 Slideshow e Apresentação

Abra uma pasta com fotos de família ou slides de apresentação. Toque em "Reproduzir" para iniciar um slideshow. Use as configurações por recurso para ajustar a duração dos slides como preferir.

### 5. ⭐ Gerenciando Favoritos

Marque arquivos importantes com o botão de estrela durante a navegação. Depois, toque na aba "Favoritos" no menu principal para acessar instantaneamente todos os seus arquivos favoritos de todas as fontes em um só lugar - perfeito para criar uma coleção selecionada das suas melhores mídias.

### 6. 🎶 Slideshow com Música de Fundo

Adicione sua coleção de músicas como um recurso. Em **Configurações → Mídia → Imagens**, ative **"Tocar música durante o slideshow"** e selecione seu recurso de música. Agora, ao iniciar um slideshow das suas fotos, suas faixas favoritas tocarão em segundo plano. Toque no nome da faixa para pular para outra faixa aleatória, criando o ambiente perfeito para suas apresentações de fotos.

### 7. 🖼️ Porta-Retrato Digital em um Tablet

Transforme qualquer **tablet** Android em um lindo porta-retrato digital sempre ligado. Coloque-o em um suporte, conecte-o ao seu PC doméstico (SMB) ou ao armazenamento na nuvem - as fotos são transmitidas diretamente sem ocupar nenhum armazenamento local. Ajuste o intervalo dos slides, mantenha a tela sempre ligada, adicione música de fundo e aproveite suas memórias. Até tablets antigos e lentos de entrada funcionam perfeitamente para esse fim - o app é otimizado para reprodução contínua com poucos recursos.

### 8. 🍿 Cinema em Casa e VR

Assista às suas séries favoritas armazenadas no seu PC ou na nuvem diretamente no seu telefone ou headset de VR. Sem precisar esperar a cópia terminar ou se preocupar com espaço livre. Basta apertar o play, e o próximo episódio começará automaticamente.

**Casos de Uso em Headset de VR** - O FastMediaSorter roda nativamente em headsets de VR baseados em Android (Meta Quest, Pico e similares) sem nenhuma modificação:

- **🎬 Cinema Virtual Gigante**: Abra um vídeo do seu NAS doméstico ou do armazenamento na nuvem e assista em uma tela virtual do tamanho de uma parede inteira. Sem precisar copiar arquivos de vários gigabytes para o headset - o app transmite diretamente pela sua rede doméstica. Quando um episódio termina, o próximo começa automaticamente.
- **🎵 Player de Música Imersivo**: Inicie sua coleção de músicas no ambiente de VR. O serviço de áudio em segundo plano mantém a música tocando mesmo ao alternar entre apps ou abrir a tela inicial de VR. Os botões físicos do headset (play/pause, próxima faixa) funcionam sem tocar no controle.
- **🖼️ Porta-Retrato de VR do Tamanho de uma Parede**: Transforme seu headset de VR em uma experiência imersiva de fotos - inicie um slideshow e suas fotos preenchem uma enorme parede virtual ao seu redor. Combine com música de fundo para uma experiência cinematográfica de memórias que preenche todo o ambiente. Transmita as fotos diretamente do seu PC doméstico ou da nuvem para manter o armazenamento do headset livre.

### 9. 🧹 Organizador de Downloads

Pasta de Downloads bagunçada? Abra-a no painel de fontes, configure botões de destino para "Documentos", "Imagens" e "Instaladores". Percorra rapidamente os arquivos, visualize-os e organize-os nos lugares certos com um único toque. Você pode até organizar arquivos diretamente no seu computador de rede usando o telefone como controle remoto.

### 10. 🚗 Música no Carro com Central Multimídia Android

Instale o FastMediaSorter na sua central multimídia ou rádio automotivo com Android. Adicione pastas de música de um pendrive USB ou cartão SD - ou use o recurso virtual integrado **Toda a Música** para acessar instantaneamente toda a sua coleção sem nenhuma configuração. Os botões físicos de mídia (controles do volante, botões de volume) funcionam perfeitamente pelo serviço de áudio em segundo plano: play/pause, próxima/anterior faixa, tudo sem tocar na tela. O app lembra a posição de reprodução e retoma automaticamente na inicialização.

Com a tela **Streams** ativada, a mesma central multimídia também toca estações de rádio pela internet diretamente via dados móveis ou Wi-Fi - sem precisar de um app separado como TuneIn ou RadioDroid. Adicione qualquer URL de rádio, ou importe um catálogo de estações selecionado a partir da tela Extensions. O mini-controle fixo mostra o nome da faixa ICY atual enquanto a lista de estações permanece visível.

### 11. 📺 Central de Mídia em uma Android TV Box

Instale o FastMediaSorter em qualquer Android TV box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV ou uma box Android genérica). Conecte-se a um NAS doméstico via SMB, adicione Google Drive ou Dropbox, ou conecte um pendrive USB - tudo em um único app. Controle todo o fluxo com um controle remoto ou teclado Bluetooth: o D-pad move o foco, **OK** abre os itens, **Back** volta ao nível anterior e **Backspace** sobe uma pasta no navegador. Os botões coloridos do controle remoto correspondem a ações comuns de arquivo (**Vermelho** = Excluir, **Verde** = Copiar, **Amarelo** = Mover, **Azul** = Renomear). Inicie um slideshow em tela cheia com música de fundo na TV, ou mude para a reprodução de áudio com capa do álbum e letras. Nenhuma tela sensível ao toque é necessária.

## Documentação 📚 {#documentation-}

**🗺️ Mapa da Documentação:** [Ver todos os documentos](DOCS_MAP.md)

**🌐 Site Oficial:** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

### Fontes Canônicas (Fonte Única da Verdade)

Os arquivos a seguir devem ser tratados como as fontes oficiais para detalhes voltados ao usuário:

- [Lista Completa de Funcionalidades](FEATURES.md)
- [Mapa da Documentação](DOCS_MAP.md)
- [Histórico do Produto](PRODUCT_HISTORY.md)
- [Downloads (EN)](DOWNLOADS.md)
- [Guias Como Fazer](HOW_TO-pt.md)
- [Limitações do Programa](LIMITATIONS.md)
- [Guia de Início Rápido](QUICK_START-pt.md)
- [Termos de Serviço](TERMS_OF_SERVICE.md)

Guias detalhados estão disponíveis em vários idiomas:

**🇺🇸 Inglês:**

- [Histórico do Produto](PRODUCT_HISTORY.md)
- [Guias Como Fazer](HOW_TO-pt.md)
- [Portal Web do Launcher](launcher/index.md)
- [Portal Web do Wear OS](wear/index.md)
- [Início Rápido](QUICK_START-pt.md)
- [FAQ](FAQ-pt.md)
- [Solução de Problemas](TROUBLESHOOTING-pt.md)
- [Limitações do Programa](LIMITATIONS.md)
- [Guia de Downloads](DOWNLOADS.md)
- [Lista Completa de Funcionalidades](FEATURES.md)

**🇷🇺 Русский:**

- [История продукта](PRODUCT_HISTORY-ru.md)
- [Руководства](HOW_TO-ru.md)
- [Быстрый Старт](QUICK_START-ru.md)
- [FAQ](FAQ-ru.md)
- [Устранение неполадок](TROUBLESHOOTING-ru.md)
- [Ограничения программы](LIMITATIONS-ru.md)
- [Скачивание сборок](DOWNLOADS-ru.md)

**🇺🇦 Українська:**

- [Історія продукту](PRODUCT_HISTORY-uk.md)
- [Посібники](HOW_TO-uk.md)
- [Швидкий Старт](QUICK_START-uk.md)
- [FAQ](FAQ-uk.md)
- [Вирішення проблем](TROUBLESHOOTING-uk.md)
- [Обмеження програми](LIMITATIONS-uk.md)
- [Завантаження збірок](DOWNLOADS-uk.md)

**Documentação Técnica / para Desenvolvedores:**

- [Visão Geral da Arquitetura](ARCHITECTURE.md)
- [DevOps e Scripts de Build](DEV_OPS.md)
- [Stack Tecnológico](TECH_STACK.md)
- [Documentação do Wear OS](WEAR_OS_QUICK_START.md)
- [Componentes de Código Aberto](OPEN_SOURCE.md)

## Companion Wear OS ⌚ {#wear-os-companion-}

O FastMediaSorter inclui um app Wear OS independente e completo, além de um companion para o telefone, projetado para o formato de smartwatches.

- Navegue e reproduza pastas e favoritos do telefone pareado, do armazenamento próprio do relógio e de compartilhamentos SMB/FTP/SFTP que o relógio acessa diretamente pelo Wi-Fi
- Recursos na nuvem permanecem no telefone - o relógio não tem cliente de nuvem próprio; um arquivo da nuvem só chega até ele quando você o envia do telefone com "Enviar para.."
- Mova arquivos entre o telefone e o relógio, transmita ao vivo a partir do relógio e use pequenas ferramentas integradas (calculadora, monitor de rede, minijogo) sem abrir o app no telefone
- Interface e comportamento em tempo de execução otimizados para telas redondas e compactas
- Portal web dedicado, guias de configuração e solução de problemas para os fluxos de trabalho do relógio

Mídia, compartilhamentos de rede e transferência de arquivos estão na versão completa do app do relógio (APK direto). A versão da Google Play é um pequeno primeiro lançamento - calculadora, cronômetro, minijogo e configurações; o [portal do Wear OS](wear/index.md) indica o que cada versão tem.

Documentação do Wear OS:

- 🌟 **[Portal Web do Wear OS](wear/index.md)** - Vitrine completa de funcionalidades, capturas de tela e downloads nas lojas de apps
- [Início Rápido do Wear OS](WEAR_OS_QUICK_START.md) - Guia passo a passo de pareamento e configuração
- [Configuração do Wear OS](WEAR_OS_SETUP.md) - Arquitetura do módulo e configuração da ponte do companion
- [Seção do Wear OS em Funcionalidades](FEATURES.md#16-settings--navigation)

## Instruções de Build {#build-instructions}

### Requisitos

- Android Studio Hedgehog (2023.1.1) ou mais recente

- JDK 17+
- Android SDK 35
- Versão mínima do Android: 8.0 (API 26) para Standard/Lite/Photos/VR/noLegal; 6.0 (API 23) para Legacy

### Build

1. Clone o repositório:

    ```bash
    git clone https://github.com/SerZhyAle/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. Abra o projeto no Android Studio.
3. Aguarde a sincronização do Gradle terminar.
4. Execute o app em um emulador ou dispositivo físico.

### Comandos de Build Preferidos (Windows / PowerShell)

```powershell
.\build-debug.PS1
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat testStandardDebugUnitTest
.\gradlew.bat :app_v2:lintStandardDebug
```

### APKs Gerados 📦

Após cada build bem-sucedido, o arquivo APK gerado é copiado automaticamente para a pasta `DOWNLOADS` na raiz do projeto, com uma marca de data/hora. Você pode encontrar todo o seu histórico de builds lá.

## Testes 🧪 {#testing-}

O FastMediaSorter v2 usa o **Maestro** para testes de ponta a ponta, garantindo a qualidade e a confiabilidade do app.

### Execução Rápida de Testes

```bash
# Install Maestro - macOS/Linux (Homebrew)
brew tap mobile-dev-inc/tap
brew install maestro

# Or Linux/macOS (curl)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Windows (PowerShell as Administrator) - External: install.ps1 is the Maestro installer
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1  # External: Maestro installer
Remove-Item install.ps1  # External: Maestro installer

# Run smoke tests (2-3 minutes)
./maestro/run-tests.sh smoke    # Linux/macOS
.\maestro\run-tests.ps1 smoke   # Windows

# Or use shortcut
.\scripts\utils\run-maestro-smoke.ps1  # Windows
```

**Nota**: NÃO use `npm install -g maestro-cli` - esse é um pacote diferente e sem relação!

### Conjuntos de Testes

- **Testes de Smoke** (`maestro/smoke/`): Testes das funcionalidades principais (~2-3 min)
  - Inicialização do app e permissões
  - Navegação em arquivos locais
  - Reprodução de mídia
  - Visualização de imagens

- **Testes de Caminho Crítico** (`maestro/critical/`): Operações essenciais (~1-2 min)
  - Operações de arquivo (copiar, mover, excluir)
  - Persistência das configurações

### Documentação

- 📚 [Guia de Início Rápido](../maestro/QUICK_START.md)
- 📝 [Escrevendo Testes](../maestro/WRITING_TESTS.md)
- 🔍 [Exemplos de Testes](../maestro/EXAMPLES.md)
- 🔧 [Solução de Problemas](../maestro/TROUBLESHOOTING.md)
- 📖 [Documentação Completa](../maestro/README.md)

### Integração CI/CD

Os testes são executados automaticamente a cada push via GitHub Actions. Veja [`.github/workflows/maestro-tests.yml`](../.github/workflows/maestro-tests.yml).

## Primeiros Passos (Guia Rápido de Uso) 🚀 {#first-steps-quick-usage-guide-}

1. **Adicionando uma Pasta (Recurso):**
    - Na tela principal, toque no botão com o ícone de "Mais" (+) para adicionar um novo recurso.
    - Selecione o tipo de recurso (por exemplo, "Pasta Local").
    - Use a varredura ou adicione a pasta manualmente. Depois de adicionada, ela aparecerá na lista da tela principal.

2. **Visualizando Arquivos:**
    - Toque duas vezes (ou toque e segure) no recurso adicionado na lista.
    - A tela de navegação será aberta, onde você verá todos os arquivos de mídia dessa pasta em lista ou grade.
    - Use os botões do painel superior para ordenar, filtrar ou alternar a visualização.

3. **Reprodução e Organização:**
    - Toque em qualquer arquivo para abri-lo no player em tela cheia.
    - Use deslizes para a esquerda/direita ou as zonas de toque para navegar entre os arquivos.
    - Para operações (copiar, mover), use as zonas de toque correspondentes ou os botões do painel de controle.

4. **Configurando Pastas de Destino (Destinos):**
    - Nas configurações, na aba "Destinos", você pode definir até 30 pastas que serão usadas para organização rápida.
    - Como alternativa, ative "É Destino" na tela de edição de qualquer recurso para adicioná-lo à lista de organização rápida.
    - Depois disso, botões para copiar ou mover arquivos rapidamente para essas pastas aparecerão na tela do player.

## Stack Tecnológico {#technology-stack}

- **Linguagem**: Kotlin
- **Arquitetura**: Clean Architecture, MVVM
- **UI**: Android View System (XML), Material Design 3
- **Assincronismo**: Kotlin Coroutines & Flow
- **DI**: Hilt (Dagger)
- **Banco de Dados**: Room 2.7.0
- **Navegação**: AndroidX Navigation Component
- **Mídia**: ExoPlayer (Media3 1.2.1)
- **Carregamento de Imagens**: Glide 5.0.9 com NetworkFileModelLoader personalizado
- **Protocolos de Rede**:
  - SMB: SMBJ 0.12.1 com BouncyCastle (transitivo)
  - SFTP: JSch 0.2.26 (fork com.github.mwiede, com Ed25519 integrado)
  - FTP: Apache Commons Net 3.10.0
- **Nuvem**: Google Drive API, OneDrive (MSAL), Dropbox API com OAuth 2.0
- **OCR e Tradução**:
  - Tesseract4Android (Tesseract 5.3.x) - extração de texto para alfabetos latino e cirílico
  - Google ML Kit (Tradução, Identificação de Idioma) - tradução do texto extraído
- **Busca e Letras**: api.lyrics.ovh (API JSON)

## Versão do Build

Formato da versão: `Y.YM.MDDH.Hmm` (por exemplo, `2.60.1102.207` para 10/01/2026 20:07)

Veja [dev/CHANGELOG.md](../dev/CHANGELOG.md) para notas de lançamento detalhadas.

---

## Contribuindo 🤝

Pull requests são bem-vindos. Para mudanças maiores, abra primeiro uma issue para discutir o que você gostaria de alterar.

## Contato 📧

- **Desenvolvedor**: <sza@ukr.net>
- **Site**: [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **GitHub Issues**: [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## Licença 📄

Informações legais do projeto:

- [Termos de Serviço](TERMS_OF_SERVICE.md)
- [Política de Privacidade](PRIVACY_POLICY.md)
- [Componentes de Código Aberto](OPEN_SOURCE.md)

</div>
