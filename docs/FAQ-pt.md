---
layout: default
title: "❓ Perguntas Frequentes (FAQ)"
permalink: /docs/FAQ-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# ❓ Perguntas Frequentes (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="pt" %}

---

## Perguntas Gerais

### O que é o FastMediaSorter?
O FastMediaSorter v2 é um shell completo para um dispositivo Android: ele assume a tela inicial, reproduz suas mídias, abre transmissões ao vivo, inicia seus aplicativos, conversa com seu relógio, monitora o dispositivo e gerencia todos os arquivos que você possui - em pastas locais, em unidades de rede (SMB/SFTP/FTP) e em armazenamento na nuvem (Google Drive, OneDrive, Dropbox).

### É gratuito?
Sim! O FastMediaSorter v2 é totalmente gratuito e de código aberto.

### De qual versão do Android eu preciso?
Standard, Lite e Photos exigem Android 8.0 (API 26) ou mais recente. A versão **Legacy** é compatível com Android 6.0 (API 23) ou mais recente. **XR / noLegal** exige adicionalmente hardware de headset compatível e o caminho atual de runtime de sideload.

### Precisa de internet?
**Não** para arquivos locais. **Sim** para unidades de rede e armazenamento na nuvem.

### O app tem widgets?
Sim! O FastMediaSorter v2 traz uma variedade de widgets de tela inicial - encontre-os tocando e segurando na tela inicial → Widgets → FastMediaSorter. Eles incluem atalhos de recursos, iniciadores de slideshow e muito mais.

### O app pode substituir minha tela inicial?
Sim, nas versões **Standard** e **noLegal**. Ative **Tornar este app a tela inicial** em **Configurações → Geral** e escolha o FastMediaSorter quando o Android perguntar qual tela inicial usar. Você ganha uma área de trabalho com atalhos para suas pastas, gadgets como relógio e previsão do tempo, uma grade de apps e uma barra de tarefas. Desative a opção, ou escolha **Sair do modo launcher**, e o Android restaura sua tela inicial anterior - o layout da sua área de trabalho é mantido para a próxima vez. Veja [HOW_TO](HOW_TO-pt.md#how-to-use-the-app-as-your-home-screen) para o passo a passo completo.

### Como faço para parar de usar o app como minha tela inicial?
Três formas, qualquer uma que você encontrar primeiro:

- Abra o menu Iniciar na área de trabalho, escolha **Sair do modo launcher** e confirme.
- Desative novamente **Tornar este app a tela inicial** em **Configurações → Geral**.
- Abra a própria lista de apps de tela inicial do Android em **Configurações → Geral → Configurações do sistema de launcher → Sistema → Trocar tela inicial** e escolha o launcher que você quiser.

O layout da sua área de trabalho é mantido em todos os casos, então ativar o modo novamente o traz de volta como você deixou.

### Por que meu tablet voltou à tela inicial antiga depois de reiniciar?
Porque o firmware daquele dispositivo o restaurou, não porque o app perdeu a configuração. Alguns rádios de carro baratos e boxes Android embutidos redefinem o app de tela inicial para o de fábrica a cada reinicialização, seja qual for a sua escolha - nenhum app pode sobrepor isso. Escolha o FastMediaSorter como app de tela inicial novamente após reiniciar, e se o seu dispositivo oferecer **Sempre** em vez de **Só desta vez**, escolha **Sempre**. Se ainda assim não mantiver a escolha, esse dispositivo simplesmente não permite substituir a tela inicial.

### Posso colocar minhas próprias pastas e playlists na área de trabalho?
Sim - é para isso que a área de trabalho serve. Toque e segure em um quadrado vazio e escolha **Adicionar um item..**, depois escolha o que quiser: uma das suas pastas, um stream de rádio, um app, uma pessoa ou um gadget como o relógio ou a previsão do tempo. A nova célula aparece no quadrado que você tocou, e para uma pasta você também escolhe se ela abre em modo de navegação, slideshow ou reprodução. Para reorganizar as coisas depois, escolha **Editar a área de trabalho** no mesmo menu de toque e segurar. Veja [HOW_TO](HOW_TO-pt.md#how-to-use-the-app-as-your-home-screen) para o passo a passo completo.

---

## Operações de Arquivo

### Para onde vão os arquivos excluídos?
Os arquivos excluídos vão para uma pasta `.trash/` no mesmo local (exclusão reversível). Eles não são excluídos permanentemente até que você:
- Toque em **"Esvaziar Lixeira"** em Configurações → Gerenciamento → Exclusão de arquivos e lixeira, OU
- Exclua manualmente a pasta `.trash/`

### Posso desfazer uma exclusão/movimentação?
**Sim!** Toque no botão **"Desfazer"** (ou na zona de toque inferior direita) alguns segundos depois da operação.

> ⚠️ **Observação:** Desfazer não está disponível para exclusões de arquivos de rede (eles são excluídos permanentemente de imediato).

### Qual é a diferença entre Copiar e Mover?
- **Copiar:** Cria uma duplicata, o original permanece no lugar
- **Mover:** Realoca o arquivo, removendo-o do local original

### O que é o modo Todos os Arquivos?
O **modo Todos os Arquivos** permite usar o app como um navegador de arquivos completo em todos os diretórios. Nesse modo, o app ignora os filtros de mídia padrão e exibe todos os arquivos (incluindo ZIP, RAR, APK, EXE, PDF etc.). Você pode realizar operações de arquivo padrão, como copiar, mover, renomear, compartilhar e excluir. Para arquivos binários não suportados, uma folha inferior é aberta automaticamente, permitindo gerenciar o arquivo ou abri-lo com aplicativos externos.

### Como encontro e removo arquivos duplicados?
Abra uma pasta, toque no menu de mais opções e escolha **Encontrar Duplicatas** para revisar as correspondências você mesmo, ou **Encontrar e Excluir Duplicatas** para removê-las imediatamente. Também há **Excluir por Tamanho..** para uma limpeza rápida baseada apenas no tamanho do arquivo. A opção automática pula a confirmação, então use **Encontrar Duplicatas** primeiro se quiser conferir antes que algo seja excluído. A correspondência é baseada em conteúdo - tamanho, depois um hash rápido, depois uma verificação completa SHA-256 - então cópias renomeadas ainda são encontradas.

---

## Rede e Nuvem

### Como conecto ao meu NAS doméstico (unidade de rede)?
1. Toque em **"+"** → **Rede** → **SMB / Unidade de Rede**
2. **Opção A - Automática:** Toque em **"Buscar Rede"** para descobrir automaticamente os dispositivos disponíveis na sua rede
3. **Opção B - Manual:** Digite o endereço do servidor: `\\192.168.1.100\share` ou `smb://192.168.1.100/share`
4. Digite o usuário e a senha
5. Toque em "Conectar"

**Problemas comuns e soluções:**

| Problema | O que tentar |
|---------|------------|
| "Conexão recusada" | Abra o Firewall do Windows → permita a **porta TCP 445** de entrada. Ou desative o firewall temporariamente para testar |
| "Senha incorreta" | Tente deixar o Usuário em branco (acesso convidado). Se você usa uma conta Microsoft, digite seu **e-mail completo** como usuário |
| "Host não encontrado" | Certifique-se de que o telefone e o PC estão no **mesmo roteador Wi-Fi**. O Isolamento de AP (uma configuração de segurança do roteador) pode bloquear o tráfego entre dispositivos - desative-o nas configurações do roteador |
| A busca não encontra nada | Desative a VPN no telefone. Ative a **Descoberta de Rede** no Windows (Painel de Controle → Central de Rede e Compartilhamento → Configurações avançadas de compartilhamento). Depois tente digitar o IP manualmente |
| Navegação muito lenta | Edite o recurso → execute o **Teste de Velocidade**. Se estiver abaixo de 5 Mbps, mude o telefone para a banda Wi-Fi de 5 GHz. Desative as miniaturas de vídeo para conexões lentas |
| Funciona no Wi-Fi mas não nos dados móveis | Esperado - o SMB é um protocolo somente de rede local, não pode funcionar em dados móveis |

→ Passo a passo completo: [Guia de Configuração do SMB](howto/scenario-smb-setup-pt.md)

### Como conecto ao Google Drive?
1. Toque em **"+"** → **Nuvem** → **Google Drive**
2. Toque em "Entrar com o Google"
3. Conceda as permissões quando solicitado
4. Suas pastas do Drive vão aparecer

**Observação:** Os arquivos NÃO são baixados automaticamente - eles são transmitidos sob demanda.

### Como conecto ao OneDrive?
1. Toque em **"+"** → **Nuvem** → **OneDrive**
2. Toque em "Entrar com a Microsoft"
3. Conceda as permissões quando solicitado
4. Suas pastas do OneDrive vão aparecer

### Como conecto ao Dropbox?
1. Toque em **"+"** → **Nuvem** → **Dropbox**
2. Toque em "Entrar com o Dropbox"
3. Conceda as permissões quando solicitado
4. Suas pastas do Dropbox vão aparecer

### Posso usar SFTP ou FTP?
**Sim!** Selecione **SFTP** ou **FTP** ao adicionar uma pasta:
- **SFTP:** Seguro, requer servidor SSH (porta 22)
- **FTP:** Menos seguro, protocolo mais antigo (porta 21)

### Posso compartilhar pastas do PC com o app?
**Sim** - o Fast Media Sorter for Windows publica as pastas do PC escolhidas via SFTP e mostra um código QR / arquivo de configuração `.fmscfg`. No telefone, use **Importar do companion** ou **Ler código QR** na tela Adicionar Recurso. Veja o guia do lado do PC: [Como publicar pastas do PC no Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html). Disponível em Standard, Photos, Legacy, XR/noLegal.

### Por que as miniaturas não carregam para arquivos de rede?
As miniaturas de rede são geradas **sob demanda** para economizar largura de banda. Role devagar ou aguarde alguns segundos para elas aparecerem.

Se as miniaturas nunca carregarem:
- Verifique se a conexão está ativa: toque no recurso → se a pasta abrir, a conexão está OK
- Edite o recurso → certifique-se de que **"Carregar miniaturas"** está ativado
- Para conexões muito lentas: desative as miniaturas completamente para evitar tempos esgotados (Editar recurso → desativar miniaturas)

### A conexão fica caindo / arquivos falham ao abrir durante a reprodução
- Verifique se o Wi-Fi do seu telefone está estável (não alternando entre as bandas de 2,4 e 5 GHz)
- Alguns roteadores desconectam sessões SMB ociosas - edite o recurso → ative **"Reconectar em erro"**, se disponível
- Para reprodução de vídeo via SMB: execute o Teste de Velocidade (Editar recurso → Teste de Velocidade). Você precisa de pelo menos 10 Mbps para vídeo 1080p

---

## Organização Rápida e Destinos

### O que são pastas de "Organização Rápida"?
As pastas de Organização Rápida são pastas de destino pré-configuradas para a organização rápida de arquivos. Você pode atribuir até 30 pastas com botões numerados.

### Como configuro a Organização Rápida?
**Método 1:** Configurações → Gerenciamento → Destinos de organização rápida, depois toque em **"Adicionar à Organização Rápida"**  
**Método 2:** Edite qualquer pasta → Ative "Marcar para Organização Rápida"

### Como uso a Organização Rápida enquanto visualizo arquivos?
1. Abra uma foto/vídeo em tela cheia
2. Toque em um **botão numerado** (0-9) no painel de comandos, OU
3. Toque no **canto inferior esquerdo** (zona COPY) ou no **centro inferior** (zona MOVE)

### Posso usar as teclas numéricas em vez de tocar?
Sim - conecte um teclado físico, um controle de jogo ou um controle remoto de TV, e seus botões de Organização Rápida ganham numeração (0-9) automaticamente. Pressione o dígito correspondente para copiar ou mover o arquivo para aquele destino instantaneamente, do mesmo jeito que tocar no botão.

### Os botões de Organização Rápida não aparecem
Certifique-se de ter adicionado ao menos uma pasta de destino primeiro: Configurações → Gerenciamento → Destinos de organização rápida, depois **"Adicionar à Organização Rápida"**. Os botões só aparecem quando pelo menos um destino está configurado.

### Enviei um arquivo para a pasta errada por engano
Toque em **Desfazer** imediatamente (canto inferior direito do painel de comandos) - disponível por alguns segundos após cada operação. Se você perdeu a janela de tempo, vá até a pasta de destino e mova o arquivo de volta manualmente.

---

## Zonas de Toque

### O que são "Zonas de Toque"?
Zonas de Toque são áreas invisíveis na tela que disparam ações quando tocadas. A tela é dividida em uma grade 3x3:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
└─────────┴─────────┴─────────┘
```

### Como vejo as Zonas de Toque?
Configurações → Player → **"Sempre mostrar sobreposição das zonas de toque"**

### Posso desativar as Zonas de Toque?
Sim, basta usar os **botões do painel de comandos** em vez delas. As Zonas de Toque são opcionais.

---

## Captura de Tela e Voz

### O que é a faixa de gesto na borda esquerda?
É um menu de captura rápida que você abre com um deslize diagonal a partir da borda esquerda da tela. Ative-o em **Configurações → Gerenciamento → Gestos de borda da tela → Sobreposição de gestos**. No menu você pode tirar uma captura de tela, uma foto rápida, recortar e compartilhar a imagem atual, abrir um app ou atalho de painel, ou iniciar uma gravação de tela, vídeo ou voz - tudo sem sair do que você está vendo. Disponível em Standard e XR/noLegal.

### Como gravo uma nota de voz rápida?
Três formas: o item **Gravação de voz** no menu de mais opções, o widget de tela inicial **Gravador Rápido**, ou a ação **Iniciar gravação de áudio** do gesto de borda. Seja qual for a forma que você usar para iniciar, um controle flutuante de **Parar** permanece na tela - mesmo sobre outro app - até você tocá-lo para salvar.

---

## Entrada e Controles

### Suporta teclados físicos e controles de jogo?
**Sim!** Suporte completo a teclado, mouse e controle de jogo está disponível em todas as telas. Pressione **F1** em qualquer tela para ver os atalhos ativos daquela tela.

### Como remapeio controles / altero os atalhos de teclado?
Configurações → **Gerenciamento** → **Controles e Atalhos de Teclado** - reatribua qualquer ação a uma tecla, botão ou entrada de controle de jogo diferente. O app vem com 70 padrões integrados; toque em **Redefinir** para restaurá-los. Conflitos são destacados automaticamente.

### Como baixo um arquivo de mídia de uma URL?
Compartilhe qualquer link `http(s)` com o FastMediaSorter pela **folha de compartilhamento** do Android (de um navegador, mensageiro ou qualquer app). O FastMediaSorter vai baixar o arquivo e oferecer para salvá-lo em qualquer um dos seus recursos configurados.

---

## Desempenho e Armazenamento

### Como encontro um arquivo específico pelo nome?
Use o painel **Filtro** na Navegação: toque no ícone de filtro na barra de ferramentas, digite qualquer parte do nome do arquivo no campo de nome - a lista atualiza instantaneamente. Não é necessária uma barra de busca separada; o filtro cobre totalmente esse cenário.

### Por que o app fica lento com mais de 5000 arquivos?
O app usa **paginação** para carregar arquivos em lotes. Para coleções muito grandes:
- Ative "Desativar miniaturas" para essa pasta
- Use filtros para restringir os resultados
- Ordene por Data (mais recentes primeiro) - isso carrega os arquivos recentes primeiro e evita varrer toda a pasta de uma vez

### O app trava ou congela
1. Force o encerramento e reabra o app
2. Se travar em uma pasta específica: essa pasta pode conter um arquivo corrompido - tente abrir os arquivos um a um para identificá-lo
3. Limpe o cache: Configurações → Geral → **"Limpar Cache"** - isso resolve a maioria dos problemas de estabilidade após atualizações
4. Se os travamentos persistirem: reporte via GitHub Issues (link no final desta página) - anexe uma descrição do que você estava fazendo quando travou

### Quanto armazenamento o cache de miniaturas usa?
**Padrão:** 2 GB (configurável nas Configurações)

### Como limpo o cache?
Configurações → Geral → **"Limpar Cache"**

---

## Favoritos

### Como marco arquivos como favoritos?
Toque no **ícone de estrela** ao visualizar um arquivo.

### Onde posso ver todos os meus favoritos?
Menu principal → aba **"Favoritos"**

---

## Segurança e Privacidade

### Posso proteger pastas com senha?
**Sim!** Editar pasta → Definir **Código PIN** (4-6 dígitos)

### Meus dados são coletados?
**Não.** O FastMediaSorter NÃO coleta nem envia nenhum dado pessoal.

### Os atalhos de contato na área de trabalho do launcher precisam de acesso aos meus contatos?
**Não.** Fixar uma pessoa na área de trabalho do launcher não pede nenhuma permissão de contatos. Você escolhe a pessoa no próprio seletor de contatos do Android, o app lê aquele registro uma única vez e o mantém como um instantâneo na célula - ele nunca chega a navegar pela sua agenda. As ligações usam o número que você escolheu no seletor, então a célula disca exatamente aquele número.

Existe um grupo opcional de permissão **Contatos**, solicitável sob demanda, em **Configurações → Geral → Permissões e Acesso**. Negá-la não muda nada no comportamento acima - os atalhos continuam funcionando da mesma forma, sem permissão.

### O app salva a localização GPS nas minhas fotos?
Somente se você ativar isso. Em **Configurações → Gerenciamento → Fotografia**, ative a captura de fotos, depois ative **Geomarcar fotos** logo abaixo - o app pede a permissão de localização imediatamente, não no momento do disparo. A tela de Informações do Arquivo de uma foto geomarcada mostra a data da captura e o local do GPS a partir dos dados EXIF da foto, como um link tocável que abre seu app de mapas ou navegador.

### Posso ver como uso o app?
Sim - é opcional e desativado por padrão: ative a **Coleta de estatísticas** em **Configurações → Geral** para abrir um painel local de uso: arquivos organizados, espaço liberado, tempo de reprodução e mais, detalhado por tipo de mídia. Nada é enviado automaticamente; **Enviar ao autor** ou **Exportar** só compartilham um resumo se você escolher fazer isso.

---

## Tradução Automática

### Como funciona a tradução?
Duas etapas, ambas no seu dispositivo:
- O **Tesseract** lê o texto da imagem, em todos os idiomas suportados (Inglês, Russo, Ucraniano, Búlgaro, Bielorrusso).
- O **Google ML Kit** então traduz o que foi lido.

### O que o idioma de origem "Automático" faz?
"Automático" lê o texto com o modelo em inglês e depois descobre o idioma do que foi lido para a tradução. Para texto em cirílico, escolha o idioma de origem explicitamente (por exemplo, **Russo** ou **Ucraniano**) - caso contrário, as letras são lidas como suas equivalentes visuais em latim.

### Funciona offline?
**Sim.** Você só precisa de internet uma vez para baixar o modelo de texto do seu idioma de origem e o modelo de tradução para o seu par de idiomas.

### Por que a tradução às vezes é mais lenta?
O primeiro uso de um idioma carrega seu modelo de texto, e imagens grandes ou detalhadas demoram mais para serem lidas. As próximas execuções no mesmo idioma começam mais rápido.

### O que é o modo de tradução estilo lente?
O **modo estilo lente** exibe as traduções como uma sobreposição sobre a imagem original, de forma parecida com o Google Lens. Isso permite ver o texto traduzido em seu contexto e posição originais. Você pode ativá-lo em **Configurações → Mídia → Outros** (a opção "Sobreposição estilo lente").

O **modo padrão** mostra as traduções em uma visualização de texto separada, abaixo da imagem.

---

## Música de Fundo no Slideshow

### Como adiciono música de fundo aos slideshows?
1. Adicione uma pasta contendo arquivos de áudio como um recurso
2. Vá em **Configurações → Mídia → Imagens**
3. Ative **"Tocar música durante o slideshow"**
4. Selecione seu recurso de música na lista suspensa
5. Inicie qualquer slideshow - a música tocará automaticamente!

### Posso usar música de unidades de rede ou armazenamento na nuvem?
**Sim!** O app trata automaticamente os arquivos de rede, baixando-os para o cache antes da reprodução. Isso funciona com SMB, SFTP, FTP, Google Drive, OneDrive e Dropbox.

### Como pulo faixas durante o slideshow?
Toque no **nome da faixa** exibido durante o slideshow para pular para outra faixa aleatória do seu recurso de música.

### Funciona em todas as versões?
**Quase.** A música do slideshow precisa de suporte a áudio:
- **Standard**, **Legacy**, **XR / noLegal** - suporte completo a áudio, incluindo reprodução que continua em segundo plano
- **Lite** - reproduz arquivos de áudio locais e letras, mas não tem serviço de reprodução em segundo plano, então o som para quando o app sai do primeiro plano
- **Photos** - nenhum suporte a áudio, então não há música de slideshow

---

## Streams de Internet

### O FastMediaSorter toca rádio pela internet?
Sim. A tela **Streams** toca streams de áudio http/https (mp3/aac), rádio Icecast/Shoutcast com metadados de reprodução atual ICY, HLS (.m3u8) e DASH VOD, e fontes RTSP. Disponível em Standard, Legacy e XR / noLegal. Lite e Photos não têm a tela Streams - o recurso está ausente ali, não apenas limitado a alguns protocolos.

### Como abro a tela Streams?
Toque em **Streams** na lista suspensa da janela principal (visível quando o Streams está ativado). Você também pode acessá-la em **Configurações > Mídia > Streams**, onde fica o botão principal.

### Como adiciono uma estação de rádio?
Na tela Streams, toque em **⋮** no final da barra de ferramentas, escolha **Adicionar stream** e cole a URL da estação. Toque em Salvar. A estação aparece na lista imediatamente.

### Posso importar uma playlist?
Sim - toque em **⋮ > Importar de URL** e digite um endereço `.m3u` remoto. O mesmo menu tem **Atualizar catálogo do FastMediaSorter** para a lista selecionada (com marcadores de tópico e idioma), também disponível em **Configurações > Extensões** ou na tela de boas-vindas inicial.

### Um stream não está reproduzindo - o que faço?
Se um stream falhar, uma caixa aparece com as opções **Tentar novamente**, **Remover** e **Cancelar**. Redirecionamentos 301 entre protocolos são tratados automaticamente. Se o host estiver inativo ou muito lento, a importação do catálogo esgota o tempo rapidamente em vez de travar.

### O rádio continua tocando quando saio da tela Streams?
Depende de **Configurações > Player > Reprodução de áudio em segundo plano**. Com o áudio em segundo plano LIGADO, a reprodução continua. Com ele DESLIGADO, sair da tela para o stream e oferece a escolha Parar / Continuar tocando - o mesmo comportamento do player de áudio local.

### Posso ver miniaturas ao vivo para os streams?
Alterne o botão da barra de ferramentas do Streams para a visualização em **Grade** - cada canal aparece como um bloco com seu último quadro capturado, para você identificar o que está passando rapidamente. O bloco permanece visível mesmo depois de fechar e reabrir o app, e é atualizado com uma nova captura assim que o stream volta ao ar.

### Posso transmitir um stream para minha TV?
Sim, para streams de vídeo - toque em **Transmitir** no player e escolha um Chromecast na mesma rede Wi-Fi. Streams RTSP não podem ser transmitidos; o botão só aparece para formatos que o receptor Chromecast suporta.

---

## Wear OS

### O FastMediaSorter funciona em smartwatches com Wear OS?
**Sim!** O FastMediaSorter v2 inclui um app companion para Wear OS, e ele cresceu de um simples visualizador de arquivos locais para uma verdadeira segunda tela para sua mídia.

### O que posso fazer no relógio?
- **Navegar e reproduzir** - as pastas e favoritos do seu telefone pareado, ou o próprio armazenamento local do relógio, como uma grade de miniaturas com busca, filtro e ordenação. Áudio e vídeo tocam com modo aleatório, volume pela coroa giratória e um modo de tela apagada que mantém o som funcionando.
- **Mover arquivos nos dois sentidos** - envie uma foto, vídeo ou faixa do telefone direto para o relógio (pela própria folha de compartilhamento), ou copie um arquivo do relógio de volta para uma pasta do telefone que você escolher.
- **Grave uma nota de voz no pulso** - ela fica no relógio até você enviá-la para o telefone, então nada se perde durante a gravação.
- **Reproduza streams ao vivo** - rádio e vídeo do seu catálogo de Streams tocam direto da própria lista de canais do relógio, com seus favoritos fixados no topo.
- **Dê uma olhada sem abrir o app** - adicione um bloco do FastMediaSorter ao painel de deslizar do mostrador do seu relógio para uma visualização rápida, ou sua complicação a um mostrador compatível.
- **Pequenas ferramentas integradas** - uma calculadora, um monitor de rede e o minijogo têm cada um sua própria tela no relógio.

As configurações que você altera no telefone sincronizam com o relógio e vice-versa, então você só precisa configurar tudo uma vez.

**Observação:** O relógio nunca abre recursos da nuvem por conta própria - ele não tem cliente de nuvem, e o telefone não repassa suas pastas da nuvem a ele; um arquivo da nuvem só chega ao relógio quando você o abre no telefone e escolhe seu relógio em "Enviar para..". Na versão completa do app do relógio (APK direto), o relógio se conecta sozinho a compartilhamentos SMB, FTP e SFTP pelo Wi-Fi - os recursos de rede que você envia a ele pelo telefone. A versão da Google Play do app do relógio é um pequeno primeiro lançamento (calculadora, cronômetro, minijogo e configurações) e ainda não navega em mídia.

---

## E-books EPUB

### Como ativo o suporte a EPUB?
Configurações → Mídia → **Documentos** → **"Suportar e-books EPUB"**

**Observação:** Reinicie o app depois de ativar para que as mudanças tenham efeito.

### Como leio um livro EPUB?
1. Adicione uma pasta contendo arquivos .epub como um recurso
2. Abra a pasta - você verá os arquivos EPUB com o selo "E"
3. Toque em qualquer arquivo EPUB para abri-lo no leitor

### Posso navegar entre capítulos?
**Sim!** Use:
- **Botões Anterior/Próximo** na parte inferior
- **Deslize para a esquerda/direita** para mudar de capítulo
- **Botão de sumário** (ícone 📋) para abrir o sumário

### Posso ajustar o tamanho da fonte?
**Sim!** Durante a leitura, use os botões **-A/+A** na parte inferior para diminuir/aumentar o tamanho da fonte (faixa de 6-144px). As configurações são salvas por livro.

### Posso buscar texto no EPUB?
**Sim!** Toque no **botão de Busca** <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> para abrir o painel de busca. Digite sua consulta e navegue pelas correspondências com os botões Anterior/Próximo.

### Funciona com arquivos de rede/nuvem?
**Sim!** Os arquivos EPUB são baixados automaticamente para o cache quando abertos a partir de armazenamento SMB/SFTP/FTP/Nuvem.

### O app lembra minha posição de leitura?
**Sim!** O app salva o último capítulo que você estava lendo. Ao reabrir o livro, ele continua de onde você parou.

### E quanto ao tema claro/escuro?
O leitor de EPUB se adapta automaticamente ao tema do seu app (Configurações → Geral → Tema de cores).

---

## Operações Agendadas

### O que são Operações Agendadas?
Regras de automação baseadas em horário que executam operações de Copiar, Mover ou Excluir entre quaisquer dos seus recursos (pastas locais, NAS, nuvem) em uma programação repetitiva - mesmo quando o app está fechado.

### Onde configuro as Operações Agendadas?
Configurações → **Gerenciamento** → **Operações agendadas por cronograma**. Toque em **"+"** para adicionar uma nova regra.

### Vai rodar se meu app estiver fechado?
**Sim.** As operações são agendadas pelo **WorkManager** do Android, que as executa em segundo plano independentemente de o app estar aberto.

### Por que uma operação agendada não rodou no horário exato?
O Android pode adiar tarefas do WorkManager por alguns minutos para otimizar a bateria. Para um horário mais confiável, conceda ao app a isenção de **Otimização de Bateria** (Configurações → Geral → Otimização de bateria). O intervalo mínimo é de 15 minutos.

### A operação agendada rodou, mas copiou 0 arquivos
Geralmente isso está correto - significa que todos os arquivos já estavam presentes no destino (a operação usa "pular existentes" por padrão). Para verificar: confira o log da operação e compare a contagem de "pulados" com a de "copiados".

Se você esperava que novos arquivos fossem copiados mas isso não aconteceu:
- Certifique-se de que a **Origem** está definida para o recurso correto (por exemplo, o recurso virtual "Fotos da Câmera" - não um caminho manual que possa estar errado)
- Verifique se o recurso de destino (SMB / nuvem) estava acessível no horário agendado - se o Wi-Fi estava desligado, a execução é pulada e repetida na próxima vez

### Posso ver o que foi processado?
**Sim.** Toque em **"Ver Log"** na seção de Operações Agendadas para ver um histórico com data/hora de cada execução, incluindo os resultados por arquivo.

---

## Bloco de Clima

### De onde vem a previsão do tempo?
O bloco de clima da área de trabalho usa o **Open-Meteo.com** - um serviço de previsão do tempo gratuito e sem necessidade de chave. Dados meteorológicos por Open-Meteo.com (CC-BY 4.0).

### O app rastreia minha localização?
**Não.** O local é aquele que você mesmo digita, e nenhuma permissão de localização é solicitada. O bloco atualiza a cada 20 minutos aproximadamente e mostra a última leitura com um aviso "Última informação conhecida" quando não há conexão. Tocar nele abre o app de clima do dispositivo.

---
## Ainda tem dúvidas?

Não encontrou uma resposta acima, ou algo não está funcionando como descrito? **Entre em contato** - toda mensagem é lida e a maioria dos problemas é corrigida.

- � **Guias Como Fazer** (tarefas passo a passo): [HOW_TO-pt.md](HOW_TO-pt.md)
- 🚀 **Início Rápido:** [QUICK_START-pt.md](QUICK_START-pt.md)
- 🔧 **Solução de Problemas:** [TROUBLESHOOTING-pt.md](TROUBLESHOOTING-pt.md)
- �📧 **E-mail:** [sza@ukr.net](mailto:sza@ukr.net) - para qualquer coisa: ajuda com configuração, descrições de bugs, sugestões de funcionalidades
- 🌐 **Página do autor:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Reportar bug:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferido para bugs reproduzíveis; inclua a versão do Android e o que você estava fazendo
- 📖 **Documentação completa:** [Portal de Documentação](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Quer uma funcionalidade que ainda não existe?** Escreva - muitas funcionalidades do app foram adicionadas porque alguém pediu. Se fizer sentido para o caso de uso, ela é construída.

</div>
