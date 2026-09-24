---
layout: default
title: "📖 Guias Práticos"
permalink: /docs/HOW_TO-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 📖 Guias Práticos

Instruções passo a passo para tarefas comuns.

Este guia agora tem duas camadas:

- **Grupos de Cenários** para fluxos de trabalho mais completos do dia a dia e combinações de recursos.
- **Referência de Tarefas Principais** para receitas diretas de um único recurso, mais abaixo.

{% include lang-switcher.html doc="HOW_TO" dir="/docs/" current="pt" %}

---

## Nota: Disponibilidade de recursos por variante

Alguns recursos estão disponíveis apenas em variantes (flavors) específicas. A tabela abaixo é derivada de [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), que é gerada a partir do próprio build; a superfície XR / noLegal é mantida intencionalmente como uma única coluna porque depende do hardware do headset e das regras de build para instalação manual (sideload).

| Recurso | Standard | Lite | Photos | Legacy | XR / noLegal | FOSS |
|---------|----------|------|--------|--------|--------------|------|
| Pastas de rede (SMB, SFTP, FTP) | ✓ | ✗ | ✓ | ✓ | ✓ | ✓ |
| Armazenamento em nuvem (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✓ | ✓ | ✓ | ✗ |
| Reprodução de áudio e letras | ✓ | ✓ | ✗ | ✓ | ✓ | ✓ |
| Reprodução de áudio em segundo plano | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Streams de internet (rádio, HLS/DASH, RTSP) | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Visualizador de documentos (PDF, texto) | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Leitor de EPUB | ✓ | ✗ | ✗ | ✓ | ✓ | ✓ |
| Tradução e OCR | ✓ | ✗ | ✗ | ✓ | ✓ | ✗ |
| Edição de imagens | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Modo tela inicial (launcher) | ✓ | ✗ | ✗ | ✗ | ✓ | ✗ |

O modo tela inicial é a única linha em que a coluna combinada final se divide: ele é incluído no build de instalação manual **noLegal**, mas não no build VR/XR, onde o próprio headset fornece seu ambiente inicial.

Duas linhas para áudio, porque são duas decisões de build separadas: o **Lite reproduz arquivos de áudio locais**, incluindo letras, mas para quando o aplicativo sai do primeiro plano - ele não tem serviço de reprodução em segundo plano. O Lite não tem nenhuma tela de Streams de Internet, portanto rádio e HLS/DASH/RTSP não são apenas limitados ali, eles estão ausentes.

Se um recurso estiver marcado com "✗", escolha o build **Standard** ou **XR / noLegal** que corresponda ao seu hardware e caminho de distribuição.

---

## Índice

### Grupos de Cenários

#### Mídia doméstica, TV e fluxos de sala de estar

1. [Transforme um NAS em uma prateleira de mídia na sala de estar](#turn-a-nas-into-a-living-room-media-shelf)
2. [Execute uma apresentação de slides com música de fundo para um display de ambiente](#run-a-slideshow-with-background-music-for-a-room-display)
3. [Use o FMS em uma Android TV Box](#how-to-use-fms-on-android-tv-box)
4. [Cinema Imersivo em VR com OpenXR](#openxr-vr-immersive-cinema)

#### Viagem, leitura e fluxos de documentos

5. [Prepare uma pasta para viagens sem internet estável](#prepare-a-folder-for-travel-without-stable-internet)
6. [Leia documentos na nuvem e EPUBs em qualquer lugar](#read-cloud-documents-and-epubs-on-the-go)
7. [Traduza placas, digitalizações e capturas de tela com OCR](#translate-signs-scans-and-screenshots-with-ocr)
8. [Envie arquivos de rede para aplicativos especializados](#hand-network-files-off-to-specialist-apps)
9. [Cálculos matemáticos e de texto rápidos](#quick-math-and-text-calculations)
10. [Notas em Markdown e código na nuvem](#cloud-markdown-and-code-notes)

#### Fluxos de usuários avançados e mídia mista

11. [Organize um arquivo de fotos de família com a Classificação Rápida](#sort-a-family-photo-archive-with-quick-sort)
12. [Capture a tela com gestos de borda](#capture-the-screen-with-edge-gestures)
13. [Crie uma apresentação de slides com música de fundo](#how-to-create-slideshow-with-background-music)
14. [Leia e-books (EPUB)](#how-to-read-e-books-epub)
15. [Tradução automática](#auto-translation)
16. [Widgets inteligentes para a tela inicial](#home-screen-smart-widgets)

### Referência de Tarefas Principais

17. [Conectar-se a uma unidade de rede (SMB)](#how-to-connect-to-network-drive-smb)
18. [Conectar-se a um servidor SFTP/FTP](#how-to-connect-to-sftpftp-server)
19. [Importar um compartilhamento do Windows Companion (escaneie um código ou importe um arquivo)](#how-to-import-a-windows-companion-share)
20. [Conectar-se ao armazenamento em nuvem](#how-to-connect-to-cloud-storage)
21. [Configurar pastas de Classificação Rápida](#how-to-set-up-quick-sort-folders)
22. [Usar zonas de toque](#how-to-use-touch-zones)
23. [Editar fotos](#how-to-edit-photos)
24. [Criar uma apresentação de slides](#how-to-create-slideshow)
25. [Proteger pasta com PIN](#how-to-protect-folder-with-pin)
26. [Esvaziar a lixeira](#how-to-empty-trash)
27. [Fazer backup das configurações](#how-to-backup-settings)
28. [Visualizar arquivos de texto e PDF](#how-to-view-text-and-pdf-files)
29. [Abrir arquivos de rede em aplicativos externos](#how-to-open-network-files-in-external-apps)
30. [Visualizar letras de músicas](#how-to-view-song-lyrics)
31. [Gravar sua tela](#how-to-record-your-screen)
32. [Gravar uma nota de voz](#how-to-record-a-voice-note)
33. [Usar a câmera integrada ao aplicativo](#how-to-use-the-in-app-camera)
34. [Encontrar e excluir arquivos duplicados](#how-to-find-and-delete-duplicate-files)
35. [Visualizar suas estatísticas de uso](#how-to-view-your-usage-statistics)
36. [Usar um cartão SD ou uma unidade conectada](#how-to-use-an-sd-card-or-connected-drive)
37. [Reconectar uma pasta adicionada por caminho direto](#how-to-reconnect-a-folder-added-by-direct-path)
38. [Usar o aplicativo como sua tela inicial](#how-to-use-the-app-as-your-home-screen)
39. [Escolher onde capturas e downloads são salvos](#how-to-choose-where-captures-and-downloads-are-saved)
40. [Receber arquivos compartilhados de outro aplicativo](#how-to-receive-files-shared-from-another-app)
41. [Usar os programas integrados](#how-to-use-the-built-in-programs)
42. [Peça ao seu assistente para encontrar e abrir mídia](#how-to-ask-your-assistant-to-find-and-open-media)
43. [Criptografar um arquivo com o FileDO](#how-to-encrypt-a-file-with-filedo)

---

## Grupos de Cenários

Estas seções são intencionalmente mais variadas do que os blocos de referência principais mais abaixo. Cada cenário combina um caminho rápido com contexto, contrapartidas e as situações em que o FastMediaSorter se destaca especialmente.

> **⭐ Destaque: traga as pastas do seu PC para o celular com uma única leitura.** Execute o companion gratuito [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) no seu PC, escolha as pastas com seus vídeos, músicas, documentos ou fotos, e ele mostra um código na tela. No celular, toque em **Adicionar**, escolha **Importar por código de barras**, aponte a câmera para o código - as pastas do PC são conectadas instantaneamente, sem precisar digitar endereço, porta ou senha. Passo a passo completo: [Abra pastas do PC escaneando um código](howto/scenario-companion-share-pt.md) &bull; receita rápida: [Importar um compartilhamento do Windows Companion](#how-to-import-a-windows-companion-share).

> **⌚ Smartwatches Wear OS:** Está usando um relógio Wear OS? Veja nossos guias passo a passo para [Ouvir música no seu relógio](howto/scenario-watch-music-pt.md) e [Conectar o smartwatch a compartilhamentos de NAS e PC](howto/scenario-watch-network-pt.md).

## Mídia doméstica, TV e fluxos de sala de estar

## Transforme um NAS em uma prateleira de mídia na sala de estar {#turn-a-nas-into-a-living-room-media-shelf}

**Disponível em:** Standard, Photos, Legacy, XR/noLegal

**Caminho rápido**

1. Adicione seu NAS como um recurso SMB.
2. Execute **Escanear rede** se não quiser digitar o IP manualmente.
3. Abra o recurso a partir de uma TV box, tablet ou celular.
4. Comece a navegar por vídeos, fotos ou documentos diretamente do NAS.

**Passo a passo do cenário**

- Mantenha um único recurso SMB para toda a biblioteca da família e separe as subpastas por uso: Filmes, Fotos da Família, Digitalizações, Manuais.
- Execute **Testar conexão** uma vez durante a configuração para garantir que o recurso esteja estável antes de confiar nele do sofá.
- Se o NAS for usado a partir de uma TV box, combine-o com um teclado Bluetooth ou controle remoto da TV para uma navegação rápida.
- Se a navegação parecer lenta, abra as configurações do recurso e execute o teste de velocidade integrado antes de alterar qualquer outra coisa.

**Quando isso ajuda**

- Você quer uma única fonte central de mídia em vez de copiar os mesmos arquivos para vários dispositivos.
- Você quer que a mesma biblioteca funcione para apresentação de slides, leitura de documentos e reprodução.

**Evite isto**

- Não comece resolvendo problemas de nome de host. Use um endereço IP primeiro e otimize depois.
- Não espere que a variante Lite navegue em compartilhamentos SMB - esse build não tem nenhuma fonte de rede.

## Execute uma apresentação de slides com música de fundo para um display de ambiente {#run-a-slideshow-with-background-music-for-a-room-display}

**Disponível em:** Standard, Lite, Legacy, XR / noLegal (Photos não tem suporte a áudio)

**Caminho rápido**

1. Adicione uma fonte de imagens e uma fonte de música.
2. Em **Configurações → Mídia → Imagens**, ative **Tocar música durante a apresentação de slides**.
3. Escolha o recurso de música.
4. Abra uma pasta de fotos e pressione **Reproduzir**.

**Passo a passo do cenário**

- Use uma pasta de imagens local ou um compartilhamento NAS rápido para as transições mais suaves.
- Mantenha um recurso de música separado para faixas de fundo calmas, para que o áudio da apresentação seja previsível.
- Se a pasta contiver imagens e vídeos, lembre-se de que a música pausa automaticamente quando um vídeo começa.

**Quando isso ajuda**

- Você quer que uma TV box, tablet ou celular antigo funcione como uma moldura digital para um ambiente.
- Você quer uma única configuração que possa alternar fotos de família, fotos de eventos ou álbuns de viagem sem montar a fila manualmente.

**Evite isto**

- Não use um compartilhamento de rede muito lento tanto para imagens quanto para música se a reprodução suave for importante.

## Cinema Imersivo em VR com OpenXR {#openxr-vr-immersive-cinema}

**Disponível em:** Standard, Lite, Legacy, `vr`, noLegal (3D de olho único); `vr` e noLegal (imersão total com headset - ambos os builds trazem a visão imersiva, e ela se abre quando o aplicativo detecta um headset OpenXR e a chave geral de VR está ativada)

**Caminho rápido - ativar, configurar, assistir em 3D**

1. **3D de olho único (em toda variante, nada para ativar):** abra qualquer arquivo SBS/OU/180°/360° - ele é detectado e recortado automaticamente para um olho, de modo que fique correto em uma tela plana comum. Isso é controlado por **Configurações > Player > "Mostrar conteúdo 3D de um olho"** (ativado por padrão). Para forçar um formato específico em vez de confiar na detecção automática, abra o diálogo de Controle do player em um build `vr`/XR-noLegal e escolha um modo na aba 3D - **Detecção automática**, **Lado a lado (SBS)**, **Sobre e sob (OU)** ou **Mono (desativado)**; a escolha é lembrada para aquele arquivo.
2. **Imersão total em um Quest (build `vr` ou XR/noLegal):** com o headset colocado, toque no distintivo de VR no player enquanto um arquivo 3D estiver aberto, escolha **Abrir no VR Cinema** no menu de opções de um arquivo no Navegador, ou abra **Configurações > Mídia** e toque em **Testar imersivo** para experimentar uma amostra. Qualquer uma das três opções abre uma visão OpenXR por olho desse conteúdo.
3. **Assistir:** dentro da visão imersiva, uma faixa de HUD reúne os controles - uma barra de posição que você arrasta com o raio do controle para buscar (tempo decorrido e total ao lado), além dos seletores que se aplicam a este arquivo: faixa de áudio apenas quando houver mais de uma, legendas apenas quando o arquivo as tiver, profundidade estéreo apenas para conteúdo estéreo. **OCULTAR** e **SAIR** ficam em extremos opostos da faixa; ocultá-la a remove completamente, e um puxão do gatilho a traz de volta sem ativar o que estiver por baixo. O analógico busca 10 segundos por passo; segure o **grip** enquanto o empurra para pular entre arquivos - avançar e voltar percorrem toda a lista do recurso, não apenas o arquivo que você abriu. Na primeira entrada imersiva após a instalação, uma legenda lista todas as associações de botões do controle; qualquer toque a fecha, e o botão **AJUDA** na faixa a traz de volta a qualquer momento.

**Passo a passo do cenário**

- O 3D de olho único não precisa de headset algum - é a forma mais fácil de rever antigas filmagens SBS/OU em um celular ou tablet.
- A imersão total precisa de um Quest ou outro headset OpenXR e de um build que a inclua - o build `vr` ou o build de instalação manual XR/noLegal (veja o [Guia de instalação manual de VR](VR_SIDELOAD.md)).
- Fotos e vídeos em 360°/180° são renderizados como uma esfera/hemisfério ao seu redor uma vez dentro da visão imersiva; arquivos 2D planos simplesmente tocam de forma plana.

**Quando isso ajuda**

- Você quer rever filmagens SBS/OU/360°/180° arquivadas sem um aplicativo de mídia VR separado.
- Você tem um Quest e quer experimentar a imersão total com seus próprios arquivos hoje, aceitando que a navegação por enquanto é apenas avançar/voltar.

**Evite isto**

- Não espere que o build `vr` da Meta Horizon Store / Google Play entre no modo imersivo ainda - essa parte ainda está em desenvolvimento.
- Buscar, selecionar faixa e legenda, e a profundidade estéreo ficam na faixa de HUD dentro do headset. Operações de arquivo não - volte ao painel plano para copiar, mover ou excluir.

## Reproduzir Rádio pela Internet em um Rádio Automotivo ou Player de Áudio

**Disponível em:** Standard, Legacy, XR / noLegal - a tela de Streams está ausente no Lite e no Photos

**Caminho rápido**

1. Abra o menu suspenso da janela principal e toque em **Streams**, ou vá em **Configurações > Mídia > Streams** e ative a chave se estiver desligada.
2. Toque em **⋮** no final da barra de ferramentas, escolha **Adicionar stream** e cole a URL de qualquer estação de rádio (http:// ou https://, .m3u8, rtsp://).
3. Toque na linha da estação - o áudio começa no mini-controle inferior fixo. A lista continua rolável.
4. Para um catálogo maior, toque em **Importar** e informe uma URL remota `.m3u`, ou baixe o catálogo selecionado do FastMediaSorter na tela **Extensões**.

**Passo a passo do cenário**

- O catálogo selecionado chega com chips de tópico e idioma; filtre por gênero ou idioma pelo botão de filtro (indicador de ponto quando ativo). A chave E/OU permite combinar estações que atendam a todos os critérios ou a qualquer um deles.
- O catálogo também chega agrupado em coleções nomeadas - "Russian TV", "Radio of the former USSR", "African TV" e outras. Elas aparecem como uma faixa rolável de chips logo abaixo da barra de ferramentas; toque em uma para ver apenas seus canais, na ordem em que o curador os organizou, e toque em **Todos** para voltar. Um mesmo canal pode pertencer a várias coleções, então você pode encontrá-lo tanto por país quanto por continente. Uma coleção é mais uma condição de filtro, não uma tela separada: busca, ordenação, os filtros de gênero e idioma e seus fixados continuam funcionando dentro dela. Se o catálogo baixado não trouxer coleções, a faixa simplesmente não aparece.
- Os dois pequenos ícones à direita do campo de busca separam rádio de vídeo em um toque: toque no ícone de áudio ou de vídeo para manter apenas aquele tipo, toque novamente no que está aceso para mostrar tudo.
- Fixe suas estações favoritas no topo com o ícone de alfinete - a ordem é independente dos Favoritos globais.
- Alterne a chave de visualização da barra de ferramentas para **Grade** para ver os canais como blocos com o último quadro capturado - útil para navegar por streams de vídeo rapidamente. Sua escolha entre lista ou grade é lembrada na próxima vez que você abrir o Streams.
- Se um stream for compatível com transmissão e seu celular estiver no Wi-Fi, toque em **Transmitir** no player para enviá-lo a um Chromecast na mesma rede. Streams RTSP não podem ser transmitidos.
- Os metadados ICY de reprodução atual (nome da estação, faixa atual) aparecem no mini-controle inferior.
- Uma estação que você mesmo adicionou pode ser enviada para o seu relógio Wear OS: abra o menu **⋮** da linha e toque em **Enviar para o relógio** (o comando aparece quando a opção de Companion Wear está ativada). A estação transferida permanece no relógio mesmo com atualizações do catálogo; se o mesmo endereço aparecer depois no catálogo online, a entrada do catálogo assume o lugar.
- Streams de vídeo e RTSP abrem no player em tela cheia; pressionar Voltar retorna à lista de Streams com a posição de rolagem preservada.
- O comportamento de áudio em segundo plano segue **Configurações > Player > Reprodução de áudio em segundo plano**: com ela desativada, o áudio para quando você sai da tela e o aplicativo oferece a escolha Parar / Continuar tocando.

**Quando isso ajuda**

- Rádios automotivos Android, players de áudio e media boxes onde você quer rádio pela internet sem um aplicativo separado (TuneIn, RadioDroid, streams de rede do VLC).
- Uso tipo IPTV-lite: streams HLS/DASH VOD tocam no player em tela cheia.

**Evite isto**

- Não espere reprodução de HLS/DASH ao vivo (borda ao vivo) - apenas HLS/DASH VOD é suportado nesta versão.
- Não use a variante Lite ou Photos para Streams; nenhum dos dois builds tem entrada de Streams, então nenhum protocolo funciona ali.

## Viagem, leitura e fluxos de documentos

## Prepare uma pasta para viagens sem internet estável {#prepare-a-folder-for-travel-without-stable-internet}

**Disponível em:** Standard, Lite, Photos, Legacy, XR / noLegal (a leitura de PDF e EPUB precisa de Standard, Legacy ou XR / noLegal)

**Caminho rápido**

1. Crie ou escolha uma pasta local para a viagem.
2. Copie a mídia, os PDFs, EPUBs ou notas de que você precisa para dentro dela antes de sair do Wi-Fi.
3. Abra essa pasta uma vez no FastMediaSorter para que as miniaturas e as últimas posições fiquem prontas.
4. Use a pasta offline durante a viagem.

**Passo a passo do cenário**

- Mantenha a mídia de viagem em uma única pasta local, mesmo que os originais normalmente estejam no NAS ou na nuvem.
- Misture formatos de propósito: PDFs de embarque, EPUBs de leitura, capturas de tela e música offline podem conviver lado a lado.
- Use o painel de filtro se quiser alternar entre apenas imagens, apenas documentos ou apenas áudio enquanto estiver offline.

**Quando isso ajuda**

- Voos, trens, hotéis e áreas rurais onde o streaming em nuvem não é confiável.
- Situações em que você quer um único pacote offline em vez de procurar em vários aplicativos.

**Evite isto**

- Não espere até o último minuto para testar se os arquivos realmente abrem sem internet.

## Leia documentos na nuvem e EPUBs em qualquer lugar {#read-cloud-documents-and-epubs-on-the-go}

**Disponível em:** Standard, Legacy, XR / noLegal - Lite e Photos não conseguem ler documentos nem EPUBs; o armazenamento em nuvem também está ausente no Lite

**Caminho rápido**

1. Adicione seu provedor de nuvem em **Armazenamento em Nuvem**.
2. Abra a pasta que contém PDFs ou EPUBs.
3. Toque diretamente no arquivo a partir do recurso de nuvem.
4. Continue a leitura a partir da sua última posição salva mais tarde.

**Passo a passo do cenário**

- Use isso quando seus documentos de trabalho já estiverem no Google Drive, OneDrive ou Dropbox e você não quiser um fluxo de leitura separado.
- PDFs são melhores para arquivos de layout fixo, como passagens, manuais e contratos digitalizados.
- EPUB é melhor para leitura de textos longos, onde tamanho de fonte ajustável e navegação por capítulos importam mais do que a fidelidade do layout.

**Quando isso ajuda**

- Você alterna entre documentos de trabalho e leitura pessoal sem sair do aplicativo.
- Você mantém arquivos de viagem ou de clientes na nuvem, mas ainda quer uma interface voltada para leitura.

**Evite isto**

- Não espere leitura na nuvem no Lite - esse build não tem armazenamento em nuvem nem suporte a documentos. Photos e Legacy têm armazenamento em nuvem, mas apenas o Legacy pode abrir documentos.
- Não trate dados móveis lentos como uma experiência de leitura garantida para arquivos muito grandes.

## Traduza placas, digitalizações e capturas de tela com OCR {#translate-signs-scans-and-screenshots-with-ocr}

**Disponível em:** Standard, Legacy, XR / noLegal

**Caminho rápido**

1. Abra uma imagem, um PDF ou um arquivo de texto.
2. Mostre o painel de comandos.
3. Toque em **Traduzir**.
4. Confirme o download do modelo no primeiro uso, se necessário.

**Passo a passo do cenário**

- O aplicativo lê o texto com o Tesseract no dispositivo e o traduz com o Google ML Kit.
- Para material em cirílico, escolha o idioma de origem explicitamente (por exemplo, russo ou ucraniano) - "Automático" lê com o modelo em inglês.
- Capturas de tela, recibos, cardápios e páginas digitalizadas funcionam especialmente bem quando o texto de origem é razoavelmente nítido.

**Quando isso ajuda**

- Você está viajando, lendo manuais estrangeiros ou decodificando capturas de tela de conversas e aplicativos.
- Você precisa de tradução no próprio local em vez de copiar o texto para uma ferramenta separada primeiro.

**Evite isto**

- Não julgue a qualidade do OCR por uma foto noturna desfocada ou uma digitalização mal recortada.

## Envie arquivos de rede para aplicativos especializados {#hand-network-files-off-to-specialist-apps}

**Disponível em:** Standard, Photos, Legacy, XR/noLegal

**Caminho rápido**

1. Abra um arquivo do SMB, SFTP ou FTP.
2. Toque em **ⓘ Informações**.
3. Toque em **Baixar e abrir**.
4. Escolha o aplicativo especializado no seletor do Android.

**Passo a passo do cenário**

- Use isso quando o FastMediaSorter for o melhor navegador para armazenamento remoto, mas outro aplicativo for o melhor editor ou visualizador para um tipo de arquivo.
- Os casos de transferência mais comuns são documentos de escritório, PDFs avançados, vídeos com codecs pesados e formatos de mídia de nicho.
- A cópia baixada permanece em `Downloads`, então você pode reabri-la mais tarde mesmo que a origem remota fique offline.

**Quando isso ajuda**

- Você quer um único hub de arquivos remotos sem abrir mão das melhores ferramentas especializadas.

**Evite isto**

- Não espere ainda a transferência para a nuvem por esse mesmo fluxo.

## Cálculos matemáticos e de texto rápidos {#quick-math-and-text-calculations}

**Disponível em:** Standard, Legacy, XR / noLegal

**Caminho rápido**

1. Abra qualquer documento PDF, e-book EPUB, arquivo de texto, ou execute a tradução por OCR em uma imagem.
2. Toque e segure para selecionar qualquer bloco de texto contendo números ou equações matemáticas.
3. No menu flutuante de ações de texto, toque no botão **Calculadora**.
4. A calculadora avalia a fórmula matemática instantaneamente em uma sobreposição pop-up.

**Passo a passo do cenário**

- Selecione uma linha de texto contendo números com símbolos de operadores (como `(45 + 12) * 3`) em um PDF ou em um resultado de tradução por OCR.
- Use o menu de funções da calculadora científica integrada para operações complexas (trigonometria, raízes, potências, logaritmos).
- A calculadora mantém o histórico de cálculos entre sessões e oferece suporte a posições de memória (M+/M-/MR/MC) para acompanhamento rápido de dados.

**Quando isso ajuda**

- Você está lendo um manual, uma digitalização de captura de tela ou um documento e precisa resolver rapidamente fórmulas ou somar moedas/números sem trocar para um aplicativo de calculadora diferente.

**Evite isto**

- Não cole strings alfabéticas cruas; apenas números válidos, parênteses e operadores matemáticos podem ser interpretados.

## Notas em Markdown e código na nuvem {#cloud-markdown-and-code-notes}

**Disponível em:** Standard, Photos, Legacy, XR / noLegal (local, rede e nuvem); Lite (apenas pastas locais)

**Caminho rápido**

1. Navegue até qualquer pasta local, NAS doméstico (SMB), servidor FTP/SFTP ou unidade em nuvem (Google Drive).
2. Toque no botão **Nova nota <img src="icons/doc/ic_create_text_file.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na barra de ferramentas da pasta.
3. Digite seu conteúdo dentro do editor. O aplicativo destaca tags de Markdown e sintaxe de código.
4. Toque em **Salvar** (ou deixe o salvamento automático agir) para gravar as alterações diretamente na fonte remota.

**Passo a passo do cenário**

- Mantenha um arquivo de diário `.md` no seu Google Drive ou NAS doméstico e edite-o de qualquer dispositivo usando a edição no próprio local.
- Crie novas notas em recursos-chave com resolução automática de conflito de nomes (por exemplo, `Note_1.txt`, `Note_2.txt`).
- Veja os layouts do Markdown renderizados em modo somente leitura, ou exporte notas diretamente para serviços externos como o Google Keep.

**Quando isso ajuda**

- Você quer manter notas simples, trechos de código ou listas de tarefas diretamente nas suas unidades centrais de rede/nuvem sem fluxos de copiar e colar localmente.

**Evite isto**

- Não espere a criação de notas em armazenamento na nuvem no Lite - esse build não tem armazenamento em nuvem nem fontes de rede.

## Fluxos de usuários avançados e mídia mista

## Organize um arquivo de fotos de família com a Classificação Rápida {#sort-a-family-photo-archive-with-quick-sort}

**Disponível em:** Standard, Lite, Photos, Legacy, XR / noLegal

**Caminho rápido**

1. Adicione suas pastas de destino à **Classificação Rápida**.
2. Abra a pasta de origem com as fotos de família não organizadas.
3. Use botões numerados ou zonas de toque enquanto revisa as imagens.
4. Envie as escolhidas para as pastas de destino imediatamente.

**Passo a passo do cenário**

- Crie pastas de destino por resultado, não apenas por data: `Melhores`, `Imprimir`, `Enviar para a família`, `Arquivo`.
- Revise em tela cheia para poder decidir rapidamente e mover ou copiar sem voltar para a lista de arquivos.
- Se várias pessoas cuidam do mesmo arquivo, mantenha um esquema de nomenclatura de destino consistente antes de uma grande sessão de classificação.

**Quando isso ajuda**

- Você tem um acúmulo de aniversários, viagens, eventos escolares ou importações de celulares antigos.
- Você quer um fluxo de triagem rápido em vez de arrastar arquivos manualmente em um gerenciador de arquivos.

**Evite isto**

- Não comece a classificar antes de os destinos estarem nomeados com clareza.
- Não use Mover imediatamente se ainda não tiver certeza de quais pastas devem permanecer como o arquivo de longo prazo.

## Capture a tela com gestos de borda {#capture-the-screen-with-edge-gestures}

**Disponível em:** Standard, XR/noLegal

**Caminho rápido**

1. Vá em **Configurações → Gerenciamento → Gestos de borda da tela → Sobreposição de gestos** e ative.
2. Enquanto visualiza qualquer arquivo, deslize a partir da borda esquerda para abrir o menu de captura.
3. Escolha uma ação - a faixa se fecha e a ação é executada.

**O que a faixa pode fazer**

- Tirar uma **captura de tela** da tela atual - visualizá-la, editá-la, compartilhá-la, enviá-la para outro aplicativo ou executar a tradução por OCR nela, além de uma opção de captura silenciosa.
- **Tirar uma foto** com a câmera, depois enviá-la, editá-la ou executar a tradução por OCR nela sem sair do aplicativo.
- Iniciar uma gravação de **tela**, **vídeo** ou **áudio/voz** - veja [Como gravar sua tela](#how-to-record-your-screen) e [Como gravar uma nota de voz](#how-to-record-a-voice-note).
- **Abrir um aplicativo ou painel** que você usa com frequência.
- **Recortar e compartilhar** uma região da imagem atual.

**Bom saber**

- Enquanto a faixa está ativa, um deslize a partir da borda esquerda abre o menu de captura em vez de virar a página.
- A faixa é feita para captura com uma mão só enquanto navega - deixe-a desativada se você depende dos deslizes de página pela borda esquerda.
- O Android confirma a captura ou a gravação toda vez que você usa esse gesto, mesmo para a opção de captura silenciosa - isso é uma proteção do sistema, não algo que o aplicativo controla.

**Quando isso ajuda**

- Você quer uma captura de tela, uma foto rápida ou uma gravação sem sair do arquivo que está visualizando.

## Referência de Tarefas Principais

## Como Adicionar ou Importar um Stream de Internet

**Disponível em:** Standard, Legacy, XR / noLegal (todos os protocolos) - a tela de Streams está ausente no Lite e no Photos

**Adicionar uma única URL:**

1. Abra **Streams** no menu suspenso da janela principal.
2. Toque no botão **⋮** no final da barra de ferramentas, depois em **Adicionar stream**.
3. Cole a URL do stream (rádio http/https, .m3u8, rtsp://). Toque em **Salvar**.
4. Toque na linha para iniciar a reprodução.

**Importar uma playlist .m3u remota:**

1. Na tela Streams, toque em **⋮ > Importar de URL**.
2. Digite o endereço .m3u remoto. Toque em **Importar**.
3. Todas as estações do arquivo aparecem na lista.

**Baixar o catálogo selecionado do FastMediaSorter:**

1. Abra **Configurações > Extensões** (ou a linha de Streams do onboarding de boas-vindas).
2. Toque em **Baixar** ao lado da entrada do catálogo de Streams.
3. Após o download, as linhas do catálogo aparecem em Streams com chips de tópico/idioma e são pesquisáveis e ordenáveis.

---

## Como Conectar-se a uma Unidade de Rede (SMB) {#how-to-connect-to-network-drive-smb}

**O que você precisa:**

- NAS ou PC Windows com pasta compartilhada
- Ambos os dispositivos na mesma rede Wi-Fi
- Usuário e senha do compartilhamento

**Disponível em:** variantes Standard, Photos, Legacy, XR / noLegal

**Passos:**

1. **Toque no botão "+"** na tela principal
2. Selecione **"Pasta de rede (SMB)"**
3. Preencha os detalhes:
   - **Descoberta automática (novo):**
     1. Toque no botão **"Escanear rede"**
     2. Aguarde os dispositivos aparecerem na lista
     3. Selecione seu dispositivo na lista
     4. O endereço IP será preenchido automaticamente

   - **Entrada manual:**

     ```
     Server/Path: \\192.168.1.100\photos
     Username: john
     Password: ****
     Display Name: Home NAS (optional)
     ```

4. Toque em **"Testar conexão"** para verificar
5. Toque em **"Salvar"**

**Formatos de endereço do servidor:**

- Windows: `\\192.168.1.100\share`
- Linux/Mac: `smb://192.168.1.100/share`
- Com porta: `smb://192.168.1.100:445/share`

**Dicas:**

- Use o endereço IP (não o nome do host) para maior confiabilidade
- Ative SMB v2/v3 no NAS para segurança
- Porta SMB padrão: 445

**Solução de problemas:**
→ Veja [TROUBLESHOOTING-pt.md](TROUBLESHOOTING-pt.md)

---

## Como Conectar-se a um Servidor SFTP/FTP {#how-to-connect-to-sftpftp-server}

**O que você precisa:**

- Servidor com SSH (SFTP) ou FTP habilitado
- Porta 22 (SFTP) ou 21 (FTP) aberta
- Usuário e senha (ou chave para SFTP)

**Passos:**

1. **Toque no botão "+"** na tela principal
2. Selecione **"SFTP / FTP"**
3. Escolha o protocolo: **SFTP** ou **FTP**
4. Preencha os detalhes:

   ```
   Host: 192.168.1.100
   Port: 22 (SFTP) / 21 (FTP)
   Username: username
   Password: ****
   Remote Path: /home/user/photos (optional)
   ```

5. Toque em **"Conectar"**

**Avançado:**

- **Autenticação por chave SSH:** atualmente não suportada (apenas senha)
- **Porta personalizada:** altere o número da porta se o servidor usar uma porta não padrão

**Solução de problemas:**
→ Veja [TROUBLESHOOTING-pt.md](TROUBLESHOOTING-pt.md)

---

## Como Importar um Compartilhamento do Windows Companion {#how-to-import-a-windows-companion-share}

**O que é:** o companion é um recurso do [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) (antigamente FastMediaSorter LITE) - o sorteador de mídia gratuito para Windows do mesmo autor. Ele compartilha pastas escolhidas do PC via SFTP e exporta uma conexão pronta - sem configuração manual de servidor, sem digitar host/porta/chave no celular. Você a traz para o celular **escaneando um código QR** na tela do PC, ou **importando um arquivo `.fmscfg`**.

**Disponível em:** Standard, Photos, Legacy, XR/noLegal (a leitura de código de barras precisa de câmera; o método por arquivo funciona em todo lugar, incluindo VR)

> Prefere uma versão guiada e com capturas de tela? Veja o guia de cenário [Abra pastas do PC escaneando um código](howto/scenario-companion-share-pt.md).

**Obtenha o Fast Media Sorter for Windows:**

- Site: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Guia de publicação de pastas: [How to publish PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [última versão](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (instalador ou ZIP portátil)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: procure por "FastMediaSorter LITE" (ainda listado com o nome anterior)

**No PC:**

1. Instale e execute o **Fast Media Sorter for Windows**, abra a aba **Share** nas configurações.
2. Escolha a(s) pasta(s) a compartilhar - o aplicativo inicia o servidor SFTP, gera chaves e configura a inicialização automática por conta própria.
3. Ele mostra um **código QR** na tela. Também pode **Salvar .fmscfg** se você preferir um arquivo.

**No celular - Método A, escanear o código (mais rápido):**

1. **Toque no botão "+"** na tela principal.
2. Toque em **"Importar por código de barras"** - fica ao lado dos quatro cartões de tipo de recurso e no cabeçalho do formulário SFTP.
3. Aponte a câmera para o QR no PC (toque em **Lanterna** em um ambiente escuro) e confirme o diálogo **Importar acesso**.
4. Pronto - um recurso somente leitura por pasta compartilhada aparece, com a chave do servidor fixada automaticamente.

**No celular - Método B, importar o arquivo:**

1. No PC, use **Salvar .fmscfg** e transfira o arquivo para o celular (e-mail, Telegram ou um local compartilhado).
2. **Toque em "+"** -> **"SFTP / FTP"** -> **"Importar de arquivo"** e escolha o arquivo `.fmscfg`. Se ele chegou como anexo do Telegram/e-mail, basta tocar no anexo.
3. Confirme o diálogo **Importar acesso** - os recursos somente leitura aparecem.

**Observação:** tanto o código QR quanto o arquivo de configuração incorporam a senha de acesso - trate-os como uma chave, não publique a captura de tela nem o arquivo. A entrada **Importar por código de barras** fica oculta em dispositivos sem câmera e em headsets de VR; use o Método B nesses casos.

---

## Como Conectar-se ao Armazenamento em Nuvem {#how-to-connect-to-cloud-storage}

**Provedores suportados:**

- Google Drive
- OneDrive
- Dropbox

**Passos:**

1. **Toque no botão "+"** na tela principal
2. Selecione **"Armazenamento em Nuvem"**
3. Selecione o provedor: **Google Drive**, **OneDrive** ou **Dropbox**
4. Toque em **"Entrar.."**
5. Siga o fluxo de autenticação do navegador/aplicativo
6. Conceda as permissões necessárias
7. **Selecione as pastas** a sincronizar
8. Toque em **"Concluído"**

**Observações:**

- Os arquivos são **transmitidos (streaming)**, não baixados
- Requer conexão com a internet
- As edições são sincronizadas automaticamente
- Você pode desconectar a qualquer momento: Editar pasta → Remover

**Privacidade:**

- Nenhuma senha é armazenada (usa tokens OAuth)
- Os tokens podem ser revogados nas configurações de segurança do seu provedor de nuvem

---

## Verificar a Velocidade da Rede

**Suportado para:** SMB, SFTP, FTP, Nuvem (Google Drive)

**Verificação automática:**
Ao adicionar um novo recurso de rede, o aplicativo executa automaticamente um teste de velocidade em segundo plano. Os resultados (velocidade de Leitura/Escrita) são salvos nas configurações do recurso.

**Verificação manual:**

1. Vá em **Gerenciar Recursos**
2. Edite um recurso de rede (ícone de lápis)
3. Role até o final
4. Toque no botão **"Velocidade"**
5. Aguarde cerca de 15 segundos por "Analisando velocidade.."
6. Veja os resultados:
   - **Velocidade de Leitura (Mbps)**
   - **Velocidade de Escrita (Mbps)**
   - **Threads Recomendadas** (para desempenho ideal)

---

## Como Configurar Pastas de Classificação Rápida {#how-to-set-up-quick-sort-folders}

**Método 1: Pelas Configurações**

1. **Configurações** → aba **Gerenciamento** → **Destinos da Classificação Rápida**
2. Toque em **"Adicionar à Classificação Rápida"**
3. Selecione uma pasta existente na lista
4. A pasta recebe um número (0-9) e uma cor
5. Repita para até 30 pastas

**Método 2: Pelas Configurações da Pasta**

1. Tela principal → **Toque e segure na pasta**
2. Toque em **"Editar"** (ícone de lápis)
3. Ative **"Marcar para Classificação Rápida"**
4. Toque em **"Salvar"**

**Usando a Classificação Rápida:**

Enquanto visualiza arquivos:

- Toque no **botão numerado** (0-9) no painel de comandos
- OU toque no **canto inferior esquerdo** (zona COPIAR)
- OU toque no **canto inferior central** (zona MOVER)

O arquivo é instantaneamente copiado/movido para essa pasta!

**Com um teclado ou controle remoto de TV:** conecte um e os botões de destino ganham um selo de dígito - pressione a tecla numérica correspondente para disparar aquele destino instantaneamente, sem precisar tocar.

---

## Como Usar Zonas de Toque {#how-to-use-touch-zones}

**O que são Zonas de Toque?**

A tela é dividida em 9 áreas invisíveis para ações rápidas:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Legenda:**

1. **VOLTAR** - Retorna à lista de arquivos
2. **COPIAR** - Copia o arquivo para o destino
3. **RENOMEAR** - Renomeia o arquivo atual
4. **ANTERIOR** - Vai para o arquivo anterior
5. **MOVER** - Move o arquivo para o destino
6. **PRÓXIMO** - Vai para o próximo arquivo
7. **COMANDO** - Abre o menu de comandos
8. **EXCLUIR** - Exclui o arquivo atual
9. **REPRODUZIR** - Inicia/Para a apresentação de slides

**Ativar Sobreposição (recomendado para iniciantes):**

1. Configurações → Player
2. Ative **"Sempre mostrar sobreposição de zonas de toque"**
3. Agora você verá uma grade semitransparente

**Experimente:**

1. Abra qualquer foto
2. **Toque no canto superior direito** → Próximo arquivo
3. **Toque no canto superior esquerdo** → Arquivo anterior
4. **Toque no canto central direito** → Excluir arquivo
5. **Toque no canto central esquerdo** → Copiar arquivo

**Desativar se não for necessário:**
Configurações → Player → "Sempre mostrar sobreposição de zonas de toque" = DESATIVADO

Depois use os **botões do painel de comandos** em vez disso.

---

## Como Editar Fotos {#how-to-edit-photos}

**Operações suportadas:**

- Girar (90°, 180°, 270°)
- Espelhar (horizontal, vertical)
- Filtros (Escala de cinza, Sépia, Negativo)
- Ajustar (Brilho, Contraste, Saturação)

**Passos:**

1. **Abra uma foto** no visualizador em tela cheia
2. Toque no botão **"Editar"** (ou na zona de toque central esquerda)
3. **Escolha a operação:**
   - Girar: toque no ícone de girar
   - Espelhar: toque no ícone de espelhar
   - Filtro: selecione na lista
   - Ajustar: use os controles deslizantes
4. Toque em **"Salvar"**

**Observações:**

- O arquivo original é **sobrescrito** (sem desfazer!)
- Funciona para **arquivos locais e de rede**
- Suporta: JPG, PNG, WEBP

---

## Como Criar uma Apresentação de Slides {#how-to-create-slideshow}

**Passos:**

1. **Abra qualquer pasta** com fotos
2. Toque na **primeira foto** para abrir o visualizador
3. Toque no botão **"Reproduzir"** (ou na zona de toque inferior direita)
4. A apresentação de slides começa automaticamente

**Personalizar a velocidade:**

1. **Editar as configurações da pasta:**
   - Tela principal → Toque e segure na pasta → Editar
2. Altere o **"Intervalo da Apresentação de Slides":**
   - Rápido: 2 segundos
   - Normal: 5 segundos
   - Lento: 10 segundos
3. Toque em **"Salvar"**

**Controles durante a apresentação de slides:**

- **Toque na tela** → Pausar/Retomar
- **Deslize para a esquerda/direita** → Pular arquivos
- **Toque em "Parar"** → Sair da apresentação de slides

---

## Como Criar uma Apresentação de Slides com Música de Fundo {#how-to-create-slideshow-with-background-music}

**Requisitos:**

- Pelo menos uma pasta/recurso com arquivos de áudio (MP3, FLAC etc.)
- **Disponível em:** Standard, Lite, Legacy, XR / noLegal (Photos não tem suporte a áudio)

**Configuração:**

1. **Configurações** → aba **Mídia** → **Imagens**
2. Ative **"Tocar música durante a apresentação de slides"**
3. Toque no botão **"Selecionar Fonte de Música"**
4. Escolha um recurso que contenha seus arquivos de música
5. Toque em **"Salvar"** ou feche as configurações

**Reproduzindo a apresentação de slides com música:**

1. **Abra qualquer pasta** com fotos/imagens
2. Toque na **primeira foto** para abrir o visualizador
3. Toque no botão **"Reproduzir"** (ou na zona de toque inferior direita)
4. A apresentação de slides começa com a música de fundo tocando

**Como funciona:**

- A música toca aleatoriamente a partir do recurso de música selecionado
- Quando uma faixa termina, a próxima faixa aleatória começa automaticamente
- A música continua tocando durante as transições de imagem
- A música para quando você sai da apresentação de slides ou pausa

**Observações:**

- A música toca apenas para **imagens e GIFs** (não para vídeos/áudio)
- Quando a apresentação de slides mostra um vídeo, a música pausa automaticamente
- A música retoma ao voltar para as imagens
- Funciona com fontes de música locais e de rede (SMB, SFTP, FTP)

**Personalizar a seleção de música:**

- Adicione vários arquivos de música à sua pasta de recurso de música
- O aplicativo embaralhará aleatoriamente todos os arquivos de áudio
- Organize a música em subpastas se o recurso de música tiver "Incluir Subpastas" ativado

**Solução de problemas:**

- Se nenhuma música tocar: verifique se o recurso de música contém pelo menos um arquivo de áudio
- Se a música engasgar na rede: use uma pasta local ou uma conexão de rede mais rápida
- Para música no SMB: garanta que o recurso SMB use o protocolo `file://` (veja TROUBLESHOOTING.md)

---

## Como Proteger uma Pasta com PIN {#how-to-protect-folder-with-pin}

**Passos:**

1. Tela principal → **Toque e segure na pasta**
2. Toque em **"Editar"** (ícone de lápis)
3. Role até o campo **"Código PIN"**
4. Digite um **PIN de 4 a 6 dígitos** (por exemplo, 1234)
5. Toque em **"Salvar"**

**Agora:**

- Abrir essa pasta exige o PIN
- Impede o acesso não autorizado
- Aplica-se à navegação e à edição

**Remover o PIN:**

- Editar pasta → Limpar o campo do PIN → Salvar

**Esqueceu o PIN?**

- Não há opção de recuperação (por design, por segurança)
- Você precisará remover e adicionar a pasta novamente

---

## Como Criptografar um Arquivo com o FileDO {#how-to-encrypt-a-file-with-filedo}

Um contêiner do FileDO é um único arquivo com a extensão `.fd-sec` que guarda outro arquivo protegido por senha. O formato é o mesmo usado pelo aplicativo de desktop do FileDO, então um contêiner criado aqui abre no FileDO e vice-versa.

**Ativar os comandos:** **Configurações** → aba **Gerenciamento** → **Operações de criptografia do FileDO**. Abrir um contêiner funciona esteja essa chave ativada ou não.

**Criptografar um arquivo:**

1. No Navegador, abra o menu **⋮** do arquivo.
2. Toque em **Criptografar com FileDO**.
3. Digite a senha duas vezes e confirme.
4. O contêiner aparece ao lado do arquivo como `<nome>.fd-sec`. O arquivo original permanece intocado - exclua-o você mesmo se não precisar mais dele.

**Descriptografar um arquivo:** abra o menu **⋮** do arquivo `.fd-sec`, toque em **Descriptografar com FileDO** e digite a senha. O arquivo restaurado aparece ao lado do contêiner.

**Abrir um contêiner sem restaurá-lo:** toque no arquivo `.fd-sec` em qualquer pasta que mostre todos os tipos de arquivo. O aplicativo pede apenas a senha e abre o arquivo interno no visualizador. A cópia descriptografada permanece no armazenamento privado do aplicativo e é excluída quando você volta para a lista. Marque **Lembrar a senha e testá-la em todo arquivo .fd-sec** para pular o prompt na próxima vez.

**Onde funciona:** pastas do dispositivo, pastas escolhidas pelo seletor de pastas do sistema, e compartilhamentos SMB, SFTP e FTP. Em uma pasta escolhida pelo seletor ou em um compartilhamento de rede, o arquivo é processado como uma cópia privada, o resultado é gravado de volta com um nome temporário, relido e verificado, e só então renomeado para o lugar - um arquivo existente nunca é sobrescrito.

**Se não abrir:** a mensagem indica três causas possíveis - uma senha errada, um arquivo que nunca foi um contêiner, ou um contêiner que foi alterado. Não é possível distingui-las. Um contêiner que guarda um programa ou um script não é aberto.

**Esqueceu a senha?** Não há como recuperá-la. Uma senha vazia esconde o arquivo apenas de uma olhada casual.

---

## Como Trabalhar com Pastas (selecionar, copiar, mover)

Quando as subpastas são mostradas como itens separados na lista, uma linha de pasta se comporta como uma linha de arquivo.

**Ativar linhas de pasta:** **Configurações** → **Geral** → **Mostrar subpastas separadamente**. A mesma chave existe por recurso no editor de recursos.

**Passos:**

1. Toque na caixa de seleção de uma linha de pasta, ou toque e segure a linha, para selecionar uma única pasta. Um toque rápido ainda abre a pasta.
2. Use o menu **⋮** da linha, ou a barra de ações de seleção, para escolher **Copiar**, **Mover**, **Renomear** ou **Excluir**.
3. Escolha o destino. Arquivos e pastas na mesma seleção viajam juntos em uma única operação.
4. O destino recebe toda a estrutura - cada subpasta e arquivo dentro da pasta de origem.

**Entre tipos de recurso:** uma pasta pode ser copiada ou movida entre o dispositivo e recursos SMB, SFTP, FTP e de nuvem - a estrutura é recriada no lado receptor.

**O que é recusado, e por quê:** um destino dentro da própria pasta, ou o local atual da própria pasta, é rejeitado antes de qualquer cópia começar; um destino escolhido pelo seletor de pastas do sistema que não tem um caminho de arquivo real não pode receber pastas. A mensagem indica o motivo para que você possa escolher outro destino.

**Cancelando:** uma transferência de pasta mostra o progresso e pode ser interrompida. O que já foi gravado permanece no destino - verifique a pasta antes de recomeçar. Um movimento exclui cada item de origem somente depois que sua cópia é bem-sucedida, então nada se perde no meio do caminho.

**Enviando para segundo plano:** você não precisa ficar observando o diálogo de progresso. Dispense-o e a transferência continua em execução, permanecendo visível no Navegador como uma faixa na parte inferior mostrando a operação, a porcentagem e o arquivo em que está agora. Toque nessa faixa para trazer de volta o diálogo de progresso completo, incluindo a opção de cancelar.

---

## Como Esvaziar a Lixeira {#how-to-empty-trash}

Os arquivos excluídos vão para pastas `.trash/` e permanecem lá até serem esvaziados manualmente.

**Método 1: Limpar Toda a Lixeira**

1. **Configurações** → aba **Gerenciamento** → **Exclusão de arquivos e lixeira**
2. Toque em **"Limpar Lixeira"**
3. Confirme a exclusão
4. Todas as pastas `.trash/` em todos os recursos são esvaziadas

**Método 2: Por Pasta**

1. Use um aplicativo gerenciador de arquivos
2. Navegue até a pasta (por exemplo, `/storage/emulated/0/DCIM/Camera`)
3. Encontre a subpasta `.trash/`
4. Exclua manualmente

**Aviso:** Isso é uma **exclusão permanente**! Os arquivos não podem ser recuperados.

---

## Como Fazer Backup das Configurações {#how-to-backup-settings}

**Exportar Configurações:**

1. **Configurações** → aba **Geral** → **Backups, restauração e exportação de configurações**
2. Toque em **"Exportar Todas as Configurações para Arquivo"**
4. Escolha o local (por exemplo, Downloads)
5. O arquivo é salvo como `fastmediasorter_backup.xml`

**Restaurar Configurações:**

1. **Configurações** → aba **Geral** → **Backups, restauração e exportação de configurações**
2. Toque em **"Importar Configurações de Arquivo"**
4. Selecione o arquivo de backup
5. Toque em **"Restaurar"**
6. O aplicativo reinicia com as configurações restauradas

**O que está incluído:**
✅ Pastas de Classificação Rápida
✅ Preferências de exibição
✅ Intervalos da apresentação de slides
✅ Credenciais de rede (criptografadas)
✅ Favoritos
✅ Configurações do Modo Seguro

**NÃO incluído:**
❌ Cache de miniaturas
❌ Conteúdo da lixeira  

---

## Como Visualizar Arquivos de Texto e PDF {#how-to-view-text-and-pdf-files}

**1. Ativar o Suporte:**

1. **Configurações** → aba **Mídia** → **Documentos**
2. Ative **"Suporte a arquivos de texto (.txt, .md, .log, .json, .xml)"** e **"Suporte a documentos PDF"**
3. **Rescaneie** suas pastas para encontrar os novos arquivos.

**2. Filtrar por Tipo de Mídia:**

1. Toque no **ícone de Filtro** (funil) na tela principal (canto superior direito).
2. Use as caixas de seleção para escolher os tipos de mídia:
   - Imagens
   - Vídeos
   - Áudio
   - GIFs
   - **Texto** (Novo)
   - **PDF** (Novo)
3. Toque em **"Aplicar"** para ver apenas os arquivos selecionados.

**3. Visualizador de Texto:**

- Toque em qualquer arquivo **.txt, .md, .log, .json, .xml**.
- **Role** para ler.
- **Copiar texto:** toque e segure para selecionar e copiar.

**4. Visualizador de PDF (Novos Recursos):**

- Toque em qualquer arquivo **.pdf**.
- **Barra de Controle de Navegação (Inferior):**
  - **Anterior/Próximo:** botões grandes nas bordas.
  - **Aumentar Zoom (+):** amplia a página.
  - **Diminuir Zoom (-):** reduz a página.
- **Gestos:**
  - **Deslizar para CIMA:** vai para a próxima página.
  - **Deslizar para BAIXO:** vai para a página anterior.
  - **Pinçar:** amplia/reduz o zoom naturalmente.
  - **Toque duplo:** restaura o zoom.
  - **O zoom é mantido:** a próxima página abre no zoom e na posição em que você estava lendo; um toque duplo traz a página inteira de volta.
- **Deslocar:** arraste para mover ao redor quando ampliado.
- **Selecionar texto tocando e segurando (Android 15+):** pressione e segure uma palavra para selecioná-la diretamente da própria camada de texto da página - sem passagem de OCR, sem espera. Se a mesma palavra aparecer várias vezes na página, a que está sob seu dedo é selecionada, não a primeira. Arraste as alças para estender a seleção, depois copie ou traduza.

---

## Como Ler E-Books (EPUB) {#how-to-read-e-books-epub}

**Requisitos:**

- **Configurações** → aba **Mídia** → **Documentos** → **Suporte a e-books EPUB** deve estar ativado (ativado por padrão)
- Formato suportado: `.epub` (sem DRM)

**Recursos:**

- **Navegação por Capítulos:** deslize para a esquerda/direita ou use os botões do painel de comandos
- **Sumário:** toque no ícone de lista <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> para pular para um capítulo específico
- **Tamanho da Fonte:** ajustável (6px - 144px, padrão 18px)
- **Busca:** encontre texto dentro do livro atual
- **Temas:** adapta-se automaticamente ao modo claro/escuro

**Controles:**

1. **Abra um arquivo EPUB** na lista de arquivos
2. **Toque na tela** para alternar o painel de comandos
3. **Use os controles inferiores:**
   - `Anterior` / `próximo`: navega pelos capítulos
   - `- A` / `+ A`: diminui/aumenta o tamanho da fonte
   - `Buscar` <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: busca texto
   - `Sumário` <img src="icons/doc/ic_toc.png" alt="" width="18" height="18" style="vertical-align:text-bottom">: abre o sumário
4. **Gesto de deslize:** troca de capítulo naturalmente

**Observação:** funciona perfeitamente com arquivos locais e streams de rede (SMB/SFTP/Nuvem). Livros grandes (>50MB) em redes lentas podem levar alguns segundos para carregar inicialmente.

---

## Como Abrir Arquivos de Rede em Aplicativos Externos {#how-to-open-network-files-in-external-apps}

**Disponível para:** arquivos SMB, SFTP, FTP

**Caso de uso:** você quer abrir um documento, foto ou vídeo da sua unidade de rede em um aplicativo externo especializado (por exemplo, MS Office, Adobe Acrobat, VLC Player).

**Passos:**

1. **Navegue até o arquivo** no seu recurso de rede
2. **Toque no arquivo** para abri-lo no player/visualizador
3. **Toque no botão ⓘ (Informações)** na barra de ferramentas superior
4. **Toque no botão "Baixar e Abrir"**
5. **Aguarde o download** - o diálogo de progresso mostra a porcentagem
6. **Escolha o aplicativo** no seletor de aplicativos do Android

**O que acontece:**

- O arquivo é baixado para sua pasta `Downloads`
- O progresso é mostrado em um diálogo (0-100%)
- Após a conclusão do download, o Android mostra o seletor de aplicativos
- Você pode abrir o arquivo em qualquer aplicativo compatível

**Protocolos suportados:**

- ✅ Compartilhamentos de rede SMB/CIFS
- ✅ Servidores SFTP
- ✅ Servidores FTP
- ❌ Armazenamento em nuvem (ainda não implementado)

**Dicas:**

- Os arquivos baixados permanecem na pasta `Downloads`
- Você pode excluí-los manualmente mais tarde pelo gerenciador de arquivos
- Funciona com todos os tipos de arquivo (imagens, vídeos, documentos etc.)
- Para arquivos grandes, o download pode levar vários minutos

**Exemplos de uso:**

- Editar um documento de rede no MS Word
- Reproduzir um vídeo de rede no VLC Player
- Visualizar um PDF de rede no Adobe Acrobat
- Compartilhar uma foto de rede via aplicativos de mensagens

---

## Como Visualizar Letras de Músicas {#how-to-view-song-lyrics}

**Requisitos:**

- Arquivo de áudio (MP3, FLAC etc.) com metadados de Artista e Título.
- É necessária **conexão com a internet** (usa api.lyrics.ovh).

**Passos:**

1. **Reproduza um arquivo de áudio** no player em tela cheia.
2. Toque no botão **"Letras"** no painel de comandos superior (ou no menu de comandos).
   - *Observação: o botão só é visível para arquivos de áudio.*
3. Aguarde a busca terminar.
4. As letras serão exibidas em um diálogo com rolagem.

**Lógica de busca:**

1. O aplicativo busca pela tag de **Artista + Título**.
2. Se as tags estiverem ausentes, ele tenta interpretar o **Nome do arquivo**.

---

## Tradução Automática {#auto-translation}

Traduza automaticamente texto de imagens, PDFs e arquivos de texto: o **Tesseract** lê o texto, o Google ML Kit o traduz.

**Principais recursos:**

- **Um único mecanismo de leitura:** o **Tesseract** lê texto latino e cirílico (inglês, russo, ucraniano, búlgaro, bielorrusso); o Google ML Kit traduz o resultado e identifica seu idioma.
- **Offline:** funciona inteiramente no dispositivo (após o download inicial do modelo).
- **Sobreposição inteligente:** o texto traduzido se sobrepõe ao texto original em parágrafos legíveis.

**Configuração:**

1. **Configurações** → aba **Mídia** → **Outros**
2. Ative **"Ativar Tradução"**
3. Selecione o **Idioma de Origem**:
   - **"Automático":** lê o texto com o modelo em inglês, depois detecta o idioma do que foi lido para a tradução.
   - **Idioma Específico:** lê com o modelo daquele idioma - escolha-o para texto em cirílico (por exemplo, "Russo").
4. Selecione o **Idioma de Destino** (por exemplo, Inglês).

**Como usar:**

1. Abra uma **Imagem**, um **PDF** ou um arquivo de **Texto**.
2. Toque na tela para mostrar o **Painel de Comandos**.
3. Toque no botão **"Traduzir"** (ícone A→文).
4. **Primeira execução:**
   - Confirme o download do modelo de texto para o idioma de origem.
   - Confirme o download do modelo de tradução para o par de idiomas.
5. O texto traduzido aparecerá em uma sobreposição.

**Observação:** o primeiro uso de um idioma carrega seu modelo de texto, o que acrescenta um pequeno atraso.

## Widgets Inteligentes para a Tela Inicial {#home-screen-smart-widgets}

**Disponível em:** todas as variantes - o conjunto de widgets é incluído em todos os builds; cada widget segue sua própria capacidade, então o widget de gravador de voz precisa de um build com suporte a microfone (não Lite nem Photos), enquanto os widgets de moldura de fotos e de recursos funcionam em todo lugar

**Caminho rápido**

1. Vá até a tela inicial do Android, toque e segure, e selecione **Widgets**.
2. Arraste um widget do **FastMediaSorter** (como o Gravador de Voz Rápido 1×1 ou a Câmera OCR) para sua tela.
3. Configure a pasta de destino e as configurações de captura, depois toque em **Salvar**.
4. Use o widget para executar tarefas em um toque diretamente da sua tela inicial.

**Passo a passo do cenário**

- Use widgets 1×1 como ícones dedicados de lançamento para iniciar ações em segundo plano instantaneamente (por exemplo, toque uma vez para começar a gravar voz, toque de novo para salvá-la no seu NAS).
- Configure um **widget de Operações Agendadas** para monitorar transferências de arquivo em segundo plano ou disparar uma operação "Executar Tudo".
- Coloque um **widget de Moldura de Fotos Aleatórias** para exibir uma apresentação de slides rotativa de fotos de família buscadas diretamente de um compartilhamento SMB.

**Quando isso ajuda**

- Você quer atalhos rápidos na tela inicial para capturas diárias (recibos, memorandos de voz) sem abrir a interface principal do aplicativo.
- Você precisa de widgets claros para controlar mídia ou disparar operações agendadas instantaneamente.

**Evite isto**

- Não tente adicionar widgets se o seu launcher Android restringir a criação de widgets personalizados.

---

## Como Usar o Aplicativo como Sua Tela Inicial {#how-to-use-the-app-as-your-home-screen}

O FastMediaSorter pode assumir a tela inicial do seu dispositivo e mostrar sua própria área de trabalho em vez disso - suas pastas, um relógio, o clima, seus aplicativos e uma barra de tarefas ao longo de uma borda. Se você já usou uma área de trabalho do Windows, isso vai parecer familiar: as coisas ficam onde você as coloca, e um botão Iniciar abre o menu. Isso é chamado de modo launcher, e ele vem apenas nos builds **Standard** e **noLegal**.

**Ativando:**

1. Abra **Configurações → Geral** e ative **Tornar este aplicativo a tela inicial**.
2. O Android pede para você confirmar. No Android 10 e versões mais recentes é uma única pergunta - "Permitir que o FastMediaSorter seja seu aplicativo Início?" - então basta permitir. Em versões mais antigas, a escolha clássica aparece na próxima vez que você pressionar Início: escolha o FastMediaSorter e toque em **Sempre**, ou **Apenas uma vez** se quiser apenas experimentar por enquanto.
3. Pressione Início. A área de trabalho aparece, já preenchida com cerca de uma dúzia de itens úteis - um relógio, o clima, suas pastas, uma caixa de busca - então o primeiro dia não é uma grade vazia.

Em uma instalação nova há um atalho: marque **Usar como tela inicial** na primeira página de boas-vindas. Isso não interrompe a configuração com um diálogo do sistema - a confirmação do Android aparece na primeira vez que você abrir **Configurações → Geral** depois.

**O que fica na área de trabalho:**

| Tipo de célula | O que faz |
|-----------|--------------|
| Atalho de recurso | Abre uma pasta que você adicionou - e você escolhe se ela abre em modo navegação, apresentação de slides ou reprodução |
| Gadget | Um relógio com segundos (toque para alarmes), o clima onde você mora, o que está tocando agora, um tradutor, e mais duas dezenas |
| Atalho de aplicativo | Inicia qualquer aplicativo instalado; toque e segure lista as ações rápidas daquele aplicativo |
| Célula de contato | Abre o cartão de uma pessoa, liga para ela, envia um SMS, ou abre a conversa do mensageiro dela |
| Widget de aplicativo | Os mesmos widgets que o aplicativo oferece para a tela inicial do Android, colocados aqui em vez disso |

**A barra de tarefas e o menu Iniciar:**

- A barra de tarefas fica ao longo da borda inferior e contém o botão Iniciar, os aplicativos que você usou recentemente, os que você fixou e uma pequena bandeja com o relógio, a bateria, o sinal de rede e o SIM.
- Prefere-a no topo? **Configurações → Geral → Configurações do launcher do sistema → Barra de tarefas → Posição da barra de tarefas** alterna entre **Inferior** e **Superior**. O menu Iniciar segue a barra e desce de cima quando ela está lá em cima.
- O botão Iniciar abre o menu: abrir o FastMediaSorter, seus recursos, adicionar um recurso, configurações do Android, configurações do aplicativo, configurações do launcher, editar o conteúdo da área de trabalho e, no final, reiniciar, desligar e **Sair do modo launcher**. Reiniciar e desligar só funcionam se o seu dispositivo permitir que um aplicativo comum faça isso - na maioria dos celulares eles simplesmente não farão nada.

**Seus aplicativos:** a grade de aplicativos agrupa os aplicativos em seções, cada uma com um pequeno cabeçalho. Toque em um cabeçalho para recolher uma seção que você abre raramente; cabeçalhos recolhidos se encaixam uns ao lado dos outros, então a área de trabalho fica mais curta em vez de deixar lacunas. Uma área de trabalho nova divide os aplicativos que ela semeia em dois: uma seção **Google** para os aplicativos do Google que você já tem instalados, e uma seção **Aplicativos** para os seus próprios - mensageiros, jogos e o que mais você tiver no dispositivo. Nenhum aplicativo cai nas duas. Toque e segure em qualquer aplicativo da lista para **Colocar na área de trabalho** e **Fixar na barra de tarefas**.

**Reorganizando-a:**

- Toque e segure em um quadrado vazio da área de trabalho. Quatro opções aparecem: **Adicionar um item..**, **Editar a área de trabalho**, **Papel de parede**, **Configurações do launcher**. A nova célula cai exatamente no quadrado que você pressionou.
- **Adicionar um item..** abre um seletor: um aplicativo, um recurso, uma das suas pastas, um stream de rádio, uma pessoa, uma ação do sistema, uma operação agendada, um gadget ou uma ação. Entre os gadgets estão o cartão de reprodução atual - ele mostra o que está tocando no dispositivo e leva você a esse player com um toque - e a célula do tradutor.
- **Editar a área de trabalho** ativa o modo de edição, o mesmo que **Editar conteúdo da área de trabalho** no menu Iniciar. Durante a edição: arraste uma célula para movê-la, arraste a alça do canto de um gadget para redimensioná-lo, toque em **+** para adicionar algo, e escolha **Remover da área de trabalho** em uma célula para retirá-la. Toque em **Concluído** quando terminar.
- Compartilhando o dispositivo com alguém? Ative **Bloquear área de trabalho** nas configurações do launcher - o toque e segurar então não faz nada, então o layout não pode ser alterado por acidente.
- Outros aplicativos podem colocar seus próprios atalhos aqui, exatamente como fariam em qualquer outra tela inicial.

**Retrato e paisagem são duas áreas de trabalho separadas.** O que você organiza na vertical não é o que você obtém ao virar o dispositivo de lado - cada orientação mantém seu próprio layout e suas próprias seções recolhidas. O aplicativo menciona isso uma vez, na primeira vez que você gira uma área de trabalho que organizou. As próprias configurações - posição da barra de tarefas, densidade, papel de parede - são compartilhadas por ambas.

**Encaixando mais, ou menos, na tela:** **Configurações → Geral → Configurações do launcher do sistema → Área de trabalho → Densidade da grade** oferece **Esparsa**, **Padrão**, **Densa** e **Muito densa** - células mais espaçosas, ou mais atalhos por tela.

**Voltando à sua tela inicial antiga** - qualquer uma destas três:

- Abra o menu Iniciar, escolha **Sair do modo launcher** e confirme.
- Desative **Tornar este aplicativo a tela inicial** em **Configurações → Geral**.
- Vá direto à própria lista de aplicativos de tela inicial do Android: **Configurações → Geral → Configurações do launcher do sistema → Sistema → Trocar tela inicial**.

Seu layout de área de trabalho é mantido de qualquer forma, então reativar o modo o traz de volta exatamente como você o deixou.

**Um aviso sincero.** Alguns dispositivos se recusam a lembrar a escolha. Alguns rádios automotivos genéricos baratos e outras Android boxes embutidas forçam de volta a tela inicial de fábrica toda vez que iniciam, seja qual for sua escolha. Isso é o próprio firmware do dispositivo sobrepondo você, não uma falha do aplicativo, e nenhum aplicativo pode contornar isso. Se o seu se comportar assim, escolha o FastMediaSorter como aplicativo inicial novamente após uma reinicialização - e se ainda assim não persistir, esse dispositivo simplesmente não permite isso.

---

## Como Usar o FMS em uma Android TV Box {#how-to-use-fms-on-android-tv-box}

O FastMediaSorter roda em qualquer Android TV box ou set-top box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, Android boxes genéricas). Não é necessária tela de toque - o aplicativo é totalmente operável por controle remoto de TV ou teclado Bluetooth.

**O que você precisa:**

- Android TV box rodando Android 8.0+ (Standard/Lite/Photos) ou Android 6.0+ (variante Legacy)
- Controle remoto de TV com D-pad, ou um teclado Bluetooth
- Opcional: NAS doméstico (SMB), pen drive USB ou cartão SD com mídia

**Navegação com um controle remoto de TV:**

| Botão | Ação |
|--------|--------|
| D-pad Cima/Baixo/Esquerda/Direita | Move o foco entre os itens |
| OK / Enter | Abre o item ou confirma |
| Voltar | Vai para a tela anterior |
| Backspace | Navega uma pasta acima no Navegador |
| Vermelho | Exclui o(s) arquivo(s) selecionado(s) |
| Verde | Copia o(s) arquivo(s) selecionado(s) |
| Amarelo | Move o(s) arquivo(s) selecionado(s) |
| Azul | Renomeia o arquivo selecionado |
| Canal Cima / Canal Baixo | Arquivo anterior / próximo no player |

**Passos:**

1. Instale o aplicativo pelo Google Play ou instale manualmente um APK. A variante Standard é recomendada.
2. Na tela principal, pressione **OK** no botão (+) para adicionar um recurso.
3. Escolha **Pasta Local** para armazenamento USB/SD, ou **Pasta de Rede** para conectar-se a um NAS via SMB.
4. Depois de adicionar o recurso, navegue até ele com D-pad + OK para procurar arquivos.
5. Abra qualquer vídeo, imagem ou arquivo de áudio - o player funciona totalmente pelo controle remoto.
6. Para iniciar uma apresentação de slides, abra uma pasta de imagens e navegue até o botão **Apresentação de Slides** na barra de comandos.
7. Para adicionar música de fundo à apresentação de slides, vá em **Configurações → Mídia → Imagens**, ative **Tocar música durante a apresentação de slides** e selecione seu recurso de música.

**Dicas:**

- Segure D-pad Cima/Baixo para acelerar a rolagem em listas de arquivos longas.
- Pressione **F1** em um teclado Bluetooth para abrir uma referência de atalhos específica da tela atual.
- As teclas coloridas do controle remoto de TV podem ser reatribuídas em **Configurações → Gerenciamento → Controles e atalhos de teclado**.

---

## Como Gravar Sua Tela {#how-to-record-your-screen}

**Disponível em:** Standard, XR/noLegal

**Passos:**

1. Inicie pelo menu de opções da tela principal (**Gravação de vídeo da tela**), pelo painel de Início Rápido, ou pela ação **Iniciar gravação de tela** do gesto de borda.
2. Confirme o pedido do Android para compartilhar sua tela ou apenas este aplicativo - ele aparece toda vez que você inicia uma gravação e não pode ser ignorado.
3. Uma pequena pílula no canto mostra **Gravando tela**, com controles de pausar/retomar e parar. Uma notificação também oferece **Parar**.
4. Toque em **Parar** quando terminar.

**O que acontece:**

- A gravação captura tudo na tela, incluindo outros aplicativos para os quais você mudar, junto com o áudio.
- O vídeo finalizado é salvo na pasta Movies do seu dispositivo.

**Observação:** o passo de confirmação do Android é uma proteção do sistema para qualquer coisa que grave sua tela - não é algo que o aplicativo possa desativar.

---

## Como Gravar uma Nota de Voz {#how-to-record-a-voice-note}

**Disponível em:** Standard, Legacy, XR / noLegal

**Passos:**

1. Inicie uma gravação pelo item **Gravação de voz** no menu de opções, pelo widget de tela inicial **Gravador Rápido**, ou pela ação **Iniciar gravação de áudio** do gesto de borda.
2. Fale - um indicador **Gravando..** (ou uma pílula flutuante sobre qualquer aplicativo em primeiro plano) mostra que está em execução.
3. Toque em **Parar e salvar** (ou toque novamente no widget/gesto) para finalizar.

**O que acontece:**

- A gravação é salva no destino de microfone que você escolheu nas Configurações, ou na pasta Recordings do seu dispositivo se nenhum estiver definido.
- Iniciar uma nota de voz pelo widget ou pelo gesto de borda funciona mesmo enquanto você está usando outro aplicativo - um pequeno controle flutuante permanece no topo para que você possa pará-la sem voltar ao aplicativo.

**Onde definir a pasta de salvamento:** Configurações → Gerenciamento → Gravador de voz.

---

## Como Usar a Câmera Integrada ao Aplicativo {#how-to-use-the-in-app-camera}

**Disponível em:** Standard, Lite, Photos (somente foto), Legacy, XR/noLegal

**Passos:**

1. No Navegador, abra a barra de ferramentas ou o menu de opções e toque em **Capturar com a câmera** (foto) ou **Gravar vídeo**.
2. Alterne entre **Foto** e **Vídeo** diretamente na tela da câmera se mudar de ideia.
3. Ajuste o zoom com um chip predefinido (0.5x/1x/2x..) ou o controle deslizante abaixo - ambos ficam sincronizados.
4. Toque no botão de proporção para moldar o enquadramento - **4:3**, **16:9** ou **Tela cheia**. O próprio visor muda, então o que você vê é o que a foto salva será, e a escolha é lembrada na próxima vez que você abrir a câmera (16:9 até você mudar).
5. Toque no botão de cenário de captura para escolher como a foto é tirada - normal, noturno, retrato, selfie, macro, esporte ou documento. Macro salta para a lente dedicada de foco de perto, selfie muda para a câmera frontal, esporte mantém a exposição curta para que o movimento congele, e documento é ajustado para fotografar páginas e telas planas. Apenas os cenários que seu dispositivo realmente pode oferecer são listados, o ativo é nomeado no botão, e trocar a lente manualmente retorna a câmera ao normal.
6. Toque no obturador (ou no botão de gravação) para capturar. O resultado é salvo diretamente no recurso - local ou de rede - que você estava navegando.

**Dicas:**

- Toque em qualquer lugar do visor para focar e ajustar a exposição naquele ponto - um pequeno anel marca onde.
- A ação **Iniciar gravação de vídeo** do gesto de borda abre a câmera já no modo Vídeo e começa a gravar assim que a pré-visualização estiver pronta - rápido, mas esse atalho específico salva na pasta Movies do seu dispositivo em vez do recurso navegado.
- Ative **Marcar fotos com geolocalização** perto das configurações da câmera para embutir a localização GPS em cada JPEG capturado - fica desativado até você ativá-lo. **Informações do Arquivo** então mostra a data da captura e o ponto GPS do EXIF da foto como um link tocável que abre no seu aplicativo de mapas ou navegador.

**Onde encontrar as configurações da câmera:** Configurações → Gerenciamento → Fotografia.

---

## Como Encontrar e Excluir Arquivos Duplicados {#how-to-find-and-delete-duplicate-files}

**Passos:**

1. Abra uma pasta no Navegador, depois abra o **menu de opções** <img src="icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> na barra de ferramentas.
2. Toque em **Encontrar Duplicados** para revisar as correspondências você mesmo, ou **Encontrar e Excluir Duplicados** para removê-las imediatamente.
3. Em **Encontrar Duplicados**, o aplicativo pré-seleciona cada cópia exceto a mais antiga de cada grupo - ajuste a seleção, depois toque em **Excluir Selecionados** e confirme.
4. **Encontrar e Excluir Duplicados** remove as mesmas cópias pré-selecionadas logo após a varredura, sem etapa de confirmação - use **Encontrar Duplicados** primeiro se quiser conferir antes que algo seja excluído.

**Limpar por tamanho em vez disso:**

1. No mesmo menu de opções, toque em **Excluir por Tamanho..**
2. Escolha **Menor que** ou **Maior que**, defina um tamanho e toque em **Analisar**.
3. Revise a contagem e o espaço que seria liberado, depois toque em **Excluir Arquivos** para confirmar.

**Observações:**

- A varredura corresponde os arquivos por conteúdo em três passagens - tamanho, depois um hash rápido, depois uma verificação SHA-256 completa - então duplicados renomeados ainda são detectados.
- Excluir por tamanho mostra quanto espaço você vai liberar antes que qualquer coisa seja removida; fontes de rede e nuvem pulam a lixeira, então essa exclusão é imediata e permanente.

---

## Como Visualizar Suas Estatísticas de Uso {#how-to-view-your-usage-statistics}

**Passos:**

1. Vá em **Configurações → Geral → Coleta de estatísticas** e ative.
2. Toque em **Estatísticas** (aparece logo abaixo da chave) para abrir o painel.

**O que você verá:**

- Cartões de resumo para arquivos organizados, espaço liberado e tempo gasto reproduzindo mídia.
- Um detalhamento por tipo (imagens, vídeos, áudio, documentos..).
- Seções recolhíveis com mais detalhes: operações, captura, visualização, edição, fontes e uso geral.

**Compartilhar um relatório:**

- **Enviar ao autor** abre seu aplicativo de e-mail com um resumo anexado, endereçado ao desenvolvedor.
- **Exportar** compartilha o mesmo resumo pela folha de compartilhamento padrão do Android, para que você possa salvá-lo ou enviá-lo para qualquer lugar.

**Observação:** tudo permanece no seu dispositivo até você optar por enviar ou exportar - veja o FAQ para os detalhes de privacidade.

---

## Como Usar um Cartão SD ou uma Unidade Conectada {#how-to-use-an-sd-card-or-connected-drive}

Um cartão de memória ou um pen drive USB que o celular montou contém recursos exatamente como o armazenamento interno.

**Passos:**

1. Abra **Adicionar recurso** e comece a adicionar uma pasta local. A seção **Mídia removível** aparece apenas enquanto algo está conectado, e lista cada volume com seu nome e espaço livre.
2. Toque no volume. Se o aplicativo não conseguir alcançá-lo pelo caminho, ele explica o motivo e abre o seletor de pastas do sistema - escolha o mesmo volume ali e conceda acesso à pasta que deseja.
3. O recurso entra na lista com um ícone de mídia removível, então um recurso de cartão é reconhecível à primeira vista.

**Movendo e copiando:** pastas inteiras viajam para um cartão e de volta com toda a sua estrutura de subpastas, da mesma forma que fazem entre o dispositivo e um recurso de rede.

**Sem espaço suficiente:** uma cópia ou movimentação que não cabe é recusada antes de começar, e a mensagem indica a mídia e quanto espaço está faltando - libere espaço lá ou escolha outro destino.

**Quando a mídia é ejetada:** seus recursos são marcados como indisponíveis em vez de removidos. Conecte o cartão novamente e eles funcionam sem precisar ser configurados de novo.

**No Android 6:** o sistema não relata volumes montados aos aplicativos, então a seção de mídia removível permanece vazia nesses dispositivos.

---

## Como Reconectar uma Pasta Adicionada por Caminho Direto {#how-to-reconnect-a-folder-added-by-direct-path}

Uma pasta que você adicionou digitando ou navegando até seu caminho pode mostrar suas fotos, vídeos e músicas, mas nenhum dos seus documentos. Isso não é uma varredura que os perdeu: um build de loja lê arquivos de texto, PDFs e e-books apenas por meio de uma pasta que você conectou com o seletor do sistema. Reconectar aponta o mesmo recurso para a mesma pasta por meio desse seletor, e os documentos aparecem.

**Passos:**

1. Toque no menu de três pontos no cartão da pasta na lista principal.
2. Escolha **Reconectar recurso**. O seletor de pastas do sistema se abre, já naquela pasta onde o celular permite.
3. Escolha a mesma pasta e confirme.
4. Se você escolher uma pasta diferente, o aplicativo nomeia as duas pastas e pergunta antes de mudar qualquer coisa.

**O que permanece:** o nome, a posição na sua lista, o PIN, o ícone, o papel de Classificação Rápida, seus favoritos e seus agendamentos - tudo sobrevive; o recurso é re-endereçado, não criado de novo.

**Onde você não vai vê-la:** em builds que ainda leem pastas diretamente por caminho, e no Android 10 e versões mais antigas, a entrada está ausente porque nada está faltando ali.

---

## Como Escolher Onde Capturas e Downloads São Salvos {#how-to-choose-where-captures-and-downloads-are-saved}

Fotos da câmera do aplicativo, capturas de tela, instantâneos e arquivos baixados automaticamente gravam cada um em uma pasta que você escolhe, e essa pasta não precisa ser um dos seus recursos.

**Passos:**

1. Abra a configuração do que você está salvando - captura, captura de tela, instantâneo ou download automático.
2. Escolha a pasta de destino. O navegador de pastas do sistema se abre, então você pode apontar para qualquer pasta local, incluindo uma que nunca adicionou ao aplicativo.
3. Essa pasta se torna o destino de gravação apenas para aquela configuração. Ela fica fora da sua lista geral de recursos, então escolher uma pasta de rascunho para capturas de tela não polui a tela principal.

**Dicas:**

- Cada uma das quatro configurações tem seu próprio destino - capturas de tela e fotos da câmera podem ir para lugares completamente diferentes.
- Apontar várias delas para uma pasta é tranquilo se você preferir ter tudo em um único lugar.

### Nomes dos arquivos de captura

Novas capturas usam o padrão `prefix_yyMMdd_HHmmss`. Os prefixos estáveis são `photo`, `screenshot`, `audio`, `video`, `screen_video` e `video_frame`, então o nome do arquivo identifica sua origem. Se já existir um nome na pasta de destino, o aplicativo adiciona o sufixo ` (2)` antes da extensão. Um nome de arquivo digitado manualmente na câmera permanece uma substituição e não é alterado.

---

## Como Receber Arquivos Compartilhados de Outro Aplicativo {#how-to-receive-files-shared-from-another-app}

A folha de compartilhamento de qualquer aplicativo pode enviar arquivos para o FastMediaSorter, que então os copia para onde você quiser.

**Passos:**

1. No outro aplicativo, compartilhe o arquivo ou arquivos e escolha **FastMediaSorter**.
2. Escolha a pasta de destino na tela de recebimento.
3. Inicie a cópia.

**Você não precisa esperar por ela.** A cópia continua em execução depois que a tela de recebimento se fecha, com uma notificação mostrando o progresso enquanto trabalha e uma notificação de resultado quando termina. Saia do aplicativo, bloqueie o dispositivo, continue com sua vida - a transferência não está presa a essa tela permanecer aberta.

Disponível nos builds Standard, Lite, Photos e Legacy.

---

## Como Usar os Programas Integrados {#how-to-use-the-built-in-programs}

**Disponível em:** todas as variantes - o menu de programas e o painel vêm em todos os builds, mas cada programa segue sua própria capacidade: o Monitor de Rede precisa de Standard ou noLegal, o companion Wear precisa de Standard ou noLegal, o mini-jogo está ausente no XR e no noLegal, e o Espelho precisa de uma câmera frontal com a captura de câmera ativada nas Configurações. A calculadora, a lanterna frontal e as Informações do sistema estão em todos os builds.

Além de navegar e reproduzir arquivos, o aplicativo traz um conjunto de pequenos programas integrados - uma calculadora, uma lâmpada de tela, um monitor de rede, um gravador de voz e mais. Eles ficam desativados por padrão: cada um é ativado pela sua própria configuração, e a maioria das chaves dedicadas fica junto em **Configurações → Gerenciamento**.

**Caminho rápido**

1. Vá em **Configurações → Gerenciamento** e ative o que você quiser - por exemplo, **Calculadora**, **Lanterna frontal**, **Monitor de Rede**, **Mini-jogo** ou **Informações do sistema**.
2. Abra o menu suspenso da janela principal. Os programas que você ativou aparecem ali.
3. Toque em um para executá-lo.

**Onde um programa aparece**

Um programa pode ser oferecido em até quatro superfícies, e cada superfície tira seu conteúdo e sua ordem da mesma lista única, então elas nunca se desalinham:

- **Menu de programas** - o menu suspenso da janela principal, e o painel de programas que o repete.
- **Painel de início rápido** - a sobreposição de acesso rápido.
- **Widget de tela inicial** - apenas para os programas que têm um; fixe-o pelo próprio seletor de widgets do aplicativo.
- **Área de trabalho do launcher** - quando você usa o aplicativo como sua tela inicial, ativar um programa adiciona sua célula automaticamente.

**O que está no conjunto**

Na ordem em que aparecem:

- **Captura rápida** - tire uma foto diretamente para o aplicativo.
- **Gravação de voz** - grave uma nota de voz.
- **Calculadora** - uma calculadora científica com histórico e posições de memória.
- **Monitor de Rede** - leituras ao vivo do link ativo, Wi-Fi, dados móveis, Bluetooth e localização, além de um traceroute que percorre a rota até um host salto a salto e continua contando quando um salto não responde nada.
- **Tradução por OCR de fotos** - fotografe texto e traduza.
- **Gravação de vídeo da tela** - grave a tela.
- **Baixar por link** - busque um arquivo a partir de um link colado.
- **Mini-jogo** - o pequeno jogo integrado ao aplicativo.
- **Informações do sistema** - um relatório do dispositivo acessível sem abrir as Configurações.
- **Companion Wear** - a tela do relógio, em builds que trazem a ponte para o Wear.
- **Lanterna frontal** - transforma a própria tela em uma lâmpada: ela abre branca no brilho máximo da janela, um deslize vertical muda o brilho, um pequeno botão no canto superior esquerdo escolhe e lembra outra cor, e um único toque a fecha. Apenas o brilho da janela é alterado, então a configuração do seu dispositivo permanece inalterada depois.
- **Lanterna d'água** - a mesma luz para mãos molhadas. Ela acende o flash da câmera e a tela juntos, depois bloqueia a tela: a hora e um breve lembrete são tudo que você vê, e tocar no vidro não faz absolutamente nada - um botão de volume a fecha. As barras do sistema também somem, então uma mão molhada não encontra um botão de navegação; um deslize deliberado ainda pode trazê-las de volta. Feita para chuva e para o banho - os dois lugares onde o vidro reage à água em vez de a você. Sair por um gesto do sistema também apaga a luz, então ela nunca fica acesa em um bolso. No relógio não há flash, então o visor sozinho é a luz. Ela não substitui o modo de bloqueio para água integrado a um relógio ou celular; nenhum aplicativo pode ativar esse.
- **Espelho** - transforma o celular em um espelho iluminado: a câmera frontal preenche a tela dentro de um campo branco brilhante que ilumina seu rosto, e a imagem é invertida da forma como um espelho de verdade a mostra, com um botão no canto para desligar a luz sem sair da tela. Predefinições de zoom - x1, x2, x3, x5 - ficam no canto inferior esquerdo e abrem em x3. Um botão de foto e um botão de vídeo salvam direto na mesma pasta que a Captura usa, o vídeo com som. O zoom, a inversão e o estado da luz de fundo são todos lembrados entre as execuções. Apenas o brilho da janela sobe, nunca a configuração do seu próprio dispositivo, então o celular volta ao normal assim que você sai. Ativado por padrão em qualquer dispositivo com câmera frontal, desde que a própria captura de câmera não esteja desativada nas Configurações.

O painel e o launcher trazem adicionalmente atalhos diretos de câmera - tirar uma foto e enviá-la, tirar uma foto e editá-la, tirar uma foto e traduzi-la, iniciar uma gravação de vídeo e abrir a pasta da câmera.

**Quando isso ajuda**

- Você quer uma calculadora ou uma lanterna sem sair do aplicativo, ou sem procurar um aplicativo separado em um celular lotado.
- Você usa o aplicativo como sua tela inicial e quer uma célula de um toque para uma ferramenta que usa com frequência.

**Evite isto**

- Não espere que a lanterna d'água sobreviva a um deslize para a tela inicial - um gesto de navegação do sistema ainda a encerra, e a luz se apaga junto.
- Não espere todos os programas em todos os builds - a lista acima é o conjunto completo, e um build sem a capacidade subjacente simplesmente não mostra aquela entrada.

---

## Peça ao Seu Assistente para Encontrar e Abrir Mídia {#how-to-ask-your-assistant-to-find-and-open-media}

**Disponível em:** todos os builds, no Android 16 e mais recentes. Versões mais antigas do Android simplesmente não oferecem o recurso, e nada no aplicativo precisa ser ativado para isso.

No Android 16+ o aplicativo registra um conjunto de ações de assistente - AppFunctions, na própria terminologia do Android - junto ao sistema. O assistente do seu dispositivo pode então chamá-las pelo nome, então você pode pedir em voz alta uma foto, um vídeo ou uma pasta de computador em vez de abrir o aplicativo e navegar até ela você mesmo.

**O que você pode pedir**

- **Buscar sua mídia** - o assistente repassa suas palavras à busca do aplicativo e mostra o que correspondeu.
- **Abrir um arquivo de mídia** - uma foto, um vídeo ou uma faixa abre diretamente no visualizador ou player do aplicativo.
- **Abrir uma pasta de computador** - uma das suas pastas de rede ou nuvem abre na tela de navegação.

**Caminho rápido**

1. Certifique-se de que o dispositivo roda Android 16 ou mais recente e tem um assistente do sistema configurado.
2. Peça ao assistente a mídia que você quer, nomeando o FastMediaSorter se o dispositivo hospedar vários aplicativos de mídia.
3. O aplicativo abre no resultado - a lista de busca, o arquivo ou a pasta que você pediu.

**Quando isso ajuda**

- Suas mãos estão ocupadas - cozinhando, dirigindo, segurando uma criança - e navegar por pastas não é uma opção.
- Você lembra o nome de um arquivo, mas não onde o guardou.

**Evite isto**

- Não espere isso abaixo do Android 16: as ações de assistente fazem parte do sistema mais novo, então em um celular mais antigo o assistente não as verá.
- Não espere que o assistente alcance uma pasta protegida por PIN - o bloqueio ainda se aplica, e a pasta pede o PIN normalmente.

---

## Precisa de Mais Ajuda?

- 📖 **Início Rápido:** [QUICK_START-pt.md](QUICK_START-pt.md)
- ❓ **FAQ:** [FAQ-pt.md](FAQ-pt.md)
- 🔧 **Solução de Problemas:** [TROUBLESHOOTING-pt.md](TROUBLESHOOTING-pt.md)

</div>

