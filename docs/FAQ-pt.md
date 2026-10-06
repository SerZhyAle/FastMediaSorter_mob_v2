---
layout: default
title: "❓ Perguntas Frequentes (FAQ)"
permalink: /docs/FAQ-pt.html
lang: pt
---

<sub class="doc-stamp">26.10.06 14:51</sub>

<div lang="pt" markdown="1">

<div lang="pt" dir="ltr" markdown="1">

# ❓ Perguntas Frequentes (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="pt" %}

---

## Perguntas Gerais

### O que é o FastMediaSorter?
FastMediaSorter combina reprodução e gestão de ficheiros locais, de rede e nuvem. Launcher, transmissões e relógio dependem da edição. O acesso requer permissões; substituir o ecrã inicial exige escolha explícita no Android.

### É gratuito?
Sim! O FastMediaSorter v2 é totalmente gratuito e de código aberto.

### De qual versão do Android eu preciso?
Abaixo estão os mínimos do código atual. VR/XR imersivo também exige headset/runtime compatível; **noLegal não é limitada a headsets**. Uma variante no código não garante APK publicado.

- standard / noLegal / lite / photos: Android 8.0 / API 26
- legacy / foss: Android 6.0 / API 23
- vr: Android 10 / API 29
- xr: Android 8.0 / API 26
- Wear OS app: API 28
- WFF v4 watchface: Wear OS 6 / API 36

[Android / SDK (EN)](TECHNICAL_REQUIREMENTS.html)

### Precisa de internet?
Ficheiros locais não precisam de internet. SMB/SFTP/FTP na LAN precisam de rede acessível, não internet pública. Nuvem e transmissões da internet exigem internet; disponibilidade conforme edição.

### O app tem widgets?
Sim! O FastMediaSorter v2 traz uma variedade de widgets de tela inicial - encontre-os tocando e segurando na tela inicial → Widgets → FastMediaSorter. Eles incluem atalhos de recursos, iniciadores de slideshow e muito mais.

### O app pode substituir minha tela inicial?
Em **Standard/noLegal**: **Definições → Geral → Janela de arranque principal → Ecrã principal do dispositivo**, depois escolher FastMediaSorter como Home Android. O **Área de trabalho como janela principal** não substitui o launcher do sistema.

[Matriz de funções (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Como faço para parar de usar o app como minha tela inicial?
Escolha **Sair do modo launcher**, outra janela inicial ou outra aplicação Home predefinida no Android. O layout fica guardado; o caminho Android varia por dispositivo.

[Matriz de funções (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Por que meu tablet voltou à tela inicial antiga depois de reiniciar?
Verifique Home predefinida e janela inicial. Escolha **Sempre** se disponível. Firmware, atualizações ou reposição podem alterar a escolha; reiniciar não prova a causa. Indique modelo e Android ao reportar.

### Posso colocar minhas próprias pastas e playlists na área de trabalho?
Sim - é para isso que a área de trabalho serve. Toque e segure em um quadrado vazio e escolha **Adicionar um item..**, depois escolha o que quiser: uma das suas pastas, um stream de rádio, um app, uma pessoa ou um gadget como o relógio ou a previsão do tempo. A nova célula aparece no quadrado que você tocou, e para uma pasta você também escolhe se ela abre em modo de navegação, slideshow ou reprodução. Para reorganizar as coisas depois, escolha **Editar a área de trabalho** no mesmo menu de toque e segurar. Veja [HOW_TO](HOW_TO-pt.html#how-to-use-the-app-as-your-home-screen) para o passo a passo completo.

---

## Operações de Arquivo

### Para onde vão os arquivos excluídos?
Com reciclagem ativa, caminhos locais comuns suportados usam `.trash/`. **Eliminação permanente**, `content://`, SMB/SFTP/FTP/nuvem e caminhos protegidos `/Android/media/` não usam esta política. Esvaziar é irreversível; nem toda eliminação se pode recuperar.

### Posso desfazer uma exclusão/movimentação?
Use **Anular** imediatamente, apenas se oferecido. Depende de operação, ecrã e caminhos. Eliminação permanente ou de rede/document-tree não se recupera pela reciclagem local; transferências de rede/nuvem nem sempre são reversíveis. Não substitui backup.

### Qual é a diferença entre Copiar e Mover?
- **Copiar:** Cria uma duplicata, o original permanece no lugar
- **Mover:** Realoca o arquivo, removendo-o do local original

### O que é o modo Todos os Arquivos?
Todos os ficheiros remove filtros multimédia **em recursos acessíveis**, sem contornar permissões Android. Outros formatos podem ser geridos ou abertos externamente; listar APK/EXE/arquivos não implica executar ou extrair.

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

Use conta NAS/Windows autorizada e partilha SMB acessível. Permita TCP **445** só na rede privada fiável e sub-rede necessária; **não desligue a firewall nem exponha SMB à internet**. Verifique permissões, endereço, rotas VPN e isolamento de convidados. Uma VPN privada permite acesso remoto, inclusive por dados móveis.

[Guia de Configuração do SMB](howto/scenario-smb-setup-pt.html)

### Como conecto ao Google Drive?
1. Toque em **"+"** → **Nuvem** → **Google Drive**
2. Toque em "Entrar com o Google"
3. Conceda as permissões quando solicitado
4. Suas pastas do Drive vão aparecer

Ficheiros abrem a pedido, mas visualização, miniaturas ou reprodução podem descarregar dados para cache. Não é sincronização automática de todo o Drive.

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
**Sim** - o Fast Media Sorter for Windows publica as pastas do PC escolhidas via SFTP e mostra um código QR / arquivo de configuração `.fmscfg`. No telefone, use **Importar do companion** ou **Ler código QR** na tela Adicionar Recurso. Veja o guia do lado do PC: [Como publicar pastas do PC no Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html).

[Matriz de funções (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Por que as miniaturas não carregam para arquivos de rede?
As miniaturas de rede são geradas **sob demanda** para economizar largura de banda. Role devagar ou aguarde alguns segundos para elas aparecerem.

Se as miniaturas nunca carregarem:
- Verifique se a conexão está ativa: toque no recurso → se a pasta abrir, a conexão está OK
- Edite o recurso → certifique-se de que **"Carregar miniaturas"** está ativado
- Para conexões muito lentas: desative as miniaturas completamente para evitar tempos esgotados (Editar recurso → desativar miniaturas)

### A conexão fica caindo / arquivos falham ao abrir durante a reprodução
Verifique Wi-Fi, servidor, credenciais e rotas VPN. Teste velocidade e reduza miniaturas. Banda depende do bitrate/codec, não só resolução; **10 Mbps não é requisito universal de 1080p**.

---

## Organização Rápida e Destinos

### O que são pastas de "Organização Rápida"?
As pastas de Organização Rápida são pastas de destino pré-configuradas para a organização rápida de arquivos. Você pode atribuir até 10 pastas com botões numerados.

### Como configuro a Organização Rápida?
**Método 1:** Configurações → Gerenciamento → Destinos de organização rápida, depois toque em **"Adicionar à Organização Rápida"**  
**Método 2:** Edite qualquer pasta → Ative "Marcar para Organização Rápida"

### Como uso a Organização Rápida enquanto visualizo arquivos?
Abra ficheiro e escolha destino no painel. Confirme **Copiar** ou **Mover** antes de executar. Zonas variam conforme média/modo; canto inferior esquerdo nem sempre copia.

### Posso usar as teclas numéricas em vez de tocar?
Sim - conecte um teclado físico, um controle de jogo ou um controle remoto de TV, e seus botões de Organização Rápida ganham numeração (0-9) automaticamente. Pressione o dígito correspondente para copiar ou mover o arquivo para aquele destino instantaneamente, do mesmo jeito que tocar no botão.

### Os botões de Organização Rápida não aparecem
Certifique-se de ter adicionado ao menos uma pasta de destino primeiro: Configurações → Gerenciamento → Destinos de organização rápida, depois **"Adicionar à Organização Rápida"**. Os botões só aparecem quando pelo menos um destino está configurado.

### Enviei um arquivo para a pasta errada por engano
Use **Anular** se disponível. Caso contrário verifique origem/destino e devolva manualmente. Copiar mantém original; não elimine cópias antes de verificar.

---

## Zonas de Toque

### O que são "Zonas de Toque"?
O mapa depende de média/modo: imagens podem usar 3×3; áudio/vídeo reservam controlos e pausa no centro. O painel usa três colunas; documentos usam gestos sem zonas de toque. A sobreposição mostra o mapa ativo.

### Como vejo as Zonas de Toque?
Configurações → Player → **"Sempre mostrar sobreposição das zonas de toque"**

### Posso desativar as Zonas de Toque?
Desative a grelha de nove zonas e use o painel. **Não desativa todos os gestos**: permanecem navegação/controlos em três colunas e gestos próprios dos documentos.

---

## Captura de Tela e Voz

### O que é a faixa de gesto na borda esquerda?
É um menu de captura rápida que você abre com um deslize diagonal a partir da borda esquerda da tela. Ative-o em **Configurações → Gerenciamento → Gestos de borda da tela → Sobreposição de gestos**. No menu você pode tirar uma captura de tela, uma foto rápida, recortar e compartilhar a imagem atual, abrir um app ou atalho de painel, ou iniciar uma gravação de tela, vídeo ou voz - tudo sem sair do que você está vendo. Disponível em Standard e XR/noLegal.

### Como gravo uma nota de voz rápida?
Três formas: o item **Gravação de voz** no menu de mais opções, o widget de tela inicial **Gravador Rápido**, ou a ação **Iniciar gravação de áudio** do gesto de borda. Seja qual for a forma que você usar para iniciar, um controle flutuante de **Parar** permanece na tela - mesmo sobre outro app - até você tocá-lo para salvar.

---

## Entrada e Controles

### Suporta teclados físicos e controles de jogo?
Teclado, rato e comando dependem do ecrã/dispositivo. **F1** mostra atalhos nos ecrãs suportados, sem garantir cada tecla em todos os diálogos.

### Como remapeio controles / altero os atalhos de teclado?
**Definições → Gestão → Controlos e teclas**: altere ações suportadas. **Reset** repõe padrões e conflitos são destacados. Consulte a lista atual, não um total fixo de 70.

### Como baixo um arquivo de mídia de uma URL?
Partilhe URL `http(s)` suportado no Android. Um ficheiro direto não é página, vídeo protegido ou DRM. Transferência e destinos graváveis dependem da edição, URL e permissões.

---

## Desempenho e Armazenamento

### Como encontro um arquivo específico pelo nome?
Use o painel **Filtro** na Navegação: toque no ícone de filtro na barra de ferramentas, digite qualquer parte do nome do arquivo no campo de nome - a lista atualiza instantaneamente. Não é necessária uma barra de busca separada; o filtro cobre totalmente esse cenário.

### Por que o app fica lento com mais de 5000 arquivos?
Pastas grandes precisam de listagem, metadados e miniaturas. Limite recursos, filtre e reduza miniaturas. Ordenar por data **não garante** evitar análise de toda a pasta; depende da fonte/formatos.

### O app trava ou congela
Reabra e verifique espaço, permissões e ligação. Limpar cache ajuda miniaturas antigas, não todos os bloqueios. Reporte versão/edição, Android, recurso e passos; retire credenciais/caminhos privados dos logs.

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
O **PIN do recurso** limita acesso na aplicação, mas **não encripta ficheiros** nem bloqueia outras aplicações ou utilizadores autorizados do servidor. Use encriptação do dispositivo/armazenamento para proteção externa.

### Meus dados são coletados?
A aplicação não envia estatísticas ao autor automaticamente. Ficam locais até exportar/enviar. Nuvem, transmissões e meteorologia contactam fornecedores escolhidos com pedidos necessários. Consulte política de privacidade.

[Política de privacidade (EN)](PRIVACY_POLICY.html)

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

Depende da edição/dispositivo. Tradução ML Kit apenas em telefones, tablets, Chromebook e desktop, não TV, automóvel, relógios ou XR. OCR separado: API 26+, pelo menos 3 GB RAM e dispositivo não low-RAM.

### O que o idioma de origem "Automático" faz?
"Automático" lê o texto com o modelo em inglês e depois descobre o idioma do que foi lido para a tradução. Para texto em cirílico, escolha o idioma de origem explicitamente (por exemplo, **Russo** ou **Ucraniano**) - caso contrário, as letras são lidas como suas equivalentes visuais em latim.

### Funciona offline?
**Sim.** Você só precisa de internet uma vez para baixar o modelo de texto do seu idioma de origem e o modelo de tradução para o seu par de idiomas.

### Por que a tradução às vezes é mais lenta?
O primeiro uso de um idioma carrega seu modelo de texto, e imagens grandes ou detalhadas demoram mais para serem lidas. As próximas execuções no mesmo idioma começam mais rápido.

### O que é o modo de tradução estilo lente?
A sobreposição mostra blocos traduzidos na imagem; o modo padrão mostra texto separado. Em dispositivos suportados: **Definições → Mídia → Tradução, digitalização (OCR) → Resultado da tradução em blocos**.

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
Standard, noLegal, Legacy, VR, XR e FOSS suportam áudio persistente em segundo plano. Lite: áudio local sem serviço persistente; Photos: sem áudio. Rede/nuvem dependem também da edição.

[Matriz de funções (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

---

## Streams de Internet

### O FastMediaSorter toca rádio pela internet?
**Transmissões** suporta rádio HTTP(S)/ICY, HLS/DASH e RTSP conforme fonte/codec. Disponível em **Standard, noLegal, Legacy, VR, XR**, não **Lite, Photos, FOSS**. URL não supera incompatibilidades ou DRM.

[Matriz de funções (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Como abro a tela Streams?
Toque em **Streams** na lista suspensa da janela principal (visível quando o Streams está ativado). Você também pode acessá-la em **Configurações > Mídia > Streams**, onde fica o botão principal.

### Como adiciono uma estação de rádio?
Na tela Streams, toque em **⋮** no final da barra de ferramentas, escolha **Adicionar stream** e cole a URL da estação. Toque em Salvar. A estação aparece na lista imediatamente.

### Posso importar uma playlist?
Sim - toque em **⋮ > Importar de URL** e digite um endereço `.m3u` remoto. O mesmo menu tem **Atualizar catálogo do FastMediaSorter** para a lista selecionada (com marcadores de tópico e idioma), também disponível em **Configurações > Extensões** ou na tela de boas-vindas inicial.

### Um stream não está reproduzindo - o que faço?
Se um stream falhar, uma caixa aparece com as opções **Tentar novamente**, **Remover** e **Cancelar**. Redirecionamentos 301 entre protocolos são tratados automaticamente. Se o host estiver inativo ou muito lento, a importação do catálogo esgota o tempo rapidamente em vez de travar.

### O rádio continua tocando quando saio da tela Streams?
Com áudio persistente suportado e ativo, a saída segue Parar / Continuar / Perguntar. Sem ele, o som para ao sair do primeiro plano. Verifique Definições → Leitor.

### Posso ver miniaturas ao vivo para os streams?
Alterne o botão da barra de ferramentas do Streams para a visualização em **Grade** - cada canal aparece como um bloco com seu último quadro capturado, para você identificar o que está passando rapidamente. O bloco permanece visível mesmo depois de fechar e reabrir o app, e é atualizado com uma nova captura assim que o stream volta ao ar.

### Posso transmitir um stream para minha TV?
Sim, para streams de vídeo - toque em **Transmitir** no player e escolha um Chromecast na mesma rede Wi-Fi. Streams RTSP não podem ser transmitidos; o botão só aparece para formatos que o receptor Chromecast suporta.

---

## Wear OS

### O FastMediaSorter funciona em smartwatches com Wear OS?
A **aplicação Wear OS** separada exige API 28 mínimo; APK no relógio. Ligação pelo telefone: **Standard/noLegal**, identificadores/assinaturas compatíveis. O **mostrador WFF v4** separado exige Wear OS 6 / API 36.

[Matriz de funções (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### O que posso fazer no relógio?
O código Wear atual inclui média local/rede, transferências com telefone, transmissões, gravação e ferramentas. Sincroniza definições/recursos suportados, não tudo do telefone. Sem cliente de nuvem independente. **Versões publicadas podem ter menos funções que o código**; consulte descrição de transferência/release.

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
Regras executam Copiar, Mover ou Eliminar suportados em recursos acessíveis. Permissões, credenciais e disponibilidade continuam necessárias. Teste primeiro com **Copiar**; eliminação agendada não é automaticamente reversível.

### Onde configuro as Operações Agendadas?
Configurações → **Gerenciamento** → **Operações agendadas por cronograma**. Toque em **"+"** para adicionar uma nova regra.

### Vai rodar se meu app estiver fechado?
WorkManager pode executar após sair, mas sem garantia após **Forçar paragem** Android, dispositivo desligado ou condições/permissões ausentes. Reabra e verifique regra/log.

### Por que uma operação agendada não rodou no horário exato?
WorkManager não é alarme exato. Bateria, rede e dispositivo podem atrasar mais de minutos. Intervalo mínimo: **15 minutos**; isenção de bateria não garante hora exata.

### A operação agendada rodou, mas copiou 0 arquivos
Verifique log: zero cópias pode ser existentes ignorados, sem correspondências, recurso indisponível ou erro de permissões. Confira origem, filtros, destino, credenciais; zero nem sempre é sucesso.

### Posso ver o que foi processado?
**Sim.** Toque em **"Ver Log"** na seção de Operações Agendadas para ver um histórico com data/hora de cada execução, incluindo os resultados por arquivo.

---

## Bloco de Clima

### De onde vem a previsão do tempo?
O bloco de clima da área de trabalho usa o **Open-Meteo.com** - um serviço de previsão do tempo gratuito e sem necessidade de chave. Dados meteorológicos por Open-Meteo.com (CC-BY 4.0).

### O app rastreia minha localização?
O **bloco meteorológico** usa local introduzido sem rastreio GPS e envia-o ao serviço. Geotags opcionais de fotos são separadas e exigem permissão de localização.

---
## Ainda tem dúvidas?

Não encontrou uma resposta acima, ou algo não está funcionando como descrito? **Entre em contato** - toda mensagem é lida e a maioria dos problemas é corrigida.

- 📖 **Guias Como Fazer** (tarefas passo a passo): [HOW_TO-pt.md](HOW_TO-pt.html)
- 🚀 **Início Rápido:** [QUICK_START-pt.md](QUICK_START-pt.html)
- 🔧 **Solução de Problemas:** [TROUBLESHOOTING-pt.md](TROUBLESHOOTING-pt.html)
- 📧 **E-mail:** [sza@ukr.net](mailto:sza@ukr.net) - para qualquer coisa: ajuda com configuração, descrições de bugs, sugestões de funcionalidades
- 🌐 **Página do autor:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Reportar bug:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferido para bugs reproduzíveis; inclua a versão do Android e o que você estava fazendo
- 📖 **Documentação completa:** [Portal de Documentação](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Quer uma funcionalidade que ainda não existe?** Escreva - muitas funcionalidades do app foram adicionadas porque alguém pediu. Se fizer sentido para o caso de uso, ela é construída.

</div>

</div>
