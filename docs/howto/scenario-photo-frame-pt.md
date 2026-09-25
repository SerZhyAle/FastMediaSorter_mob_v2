---
layout: default
title: "Porta-Retrato Digital no Tablet - FastMediaSorter v2"
permalink: /docs/howto/scenario-photo-frame-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 🖼️ Porta-Retrato Digital no Tablet

> **Nível:** Iniciante &bull; **Tempo:** ~15 minutos &bull; **Edição:** Standard, Photos, Legacy, VR, noLegal (para fotos de NAS/nuvem) ou qualquer edição (para fotos locais)

{% include lang-switcher.html doc="scenario-photo-frame" dir="/docs/howto/" current="pt" %}

Transforme qualquer tablet Android em um lindo porta-retrato digital sempre ligado - transmitindo suas memórias de um NAS de casa ou da nuvem, com música de fundo opcional. Zero armazenamento local usado.

> **A ideia em uma frase:** apoie um tablet antigo, ligue-o na tomada, inicie uma apresentação de slides - ele mostra suas fotos automaticamente, para sempre, mudando a cada poucos segundos. Como um porta-retrato digital de verdade comprado em loja, mas alimentado pela sua própria coleção de fotos de qualquer fonte.

---

## O Que Você Vai Precisar

- Um tablet Android (qualquer tamanho - um antigo funciona muito bem!)
- Um suporte ou base para manter o tablet em pé
- Um **carregador USB** para mantê-lo ligado - o tablet vai rodar o dia todo, então a bateria não é suficiente
- Suas fotos em um destes locais: **armazenamento local**, **PC/NAS de casa via SMB**, ou **Google Drive / Dropbox**
- (Opcional) Uma fonte de música para áudio de fundo

---

## Passo 1 - Adicione Sua Fonte de Fotos

Escolha onde suas fotos estão:

**Opção A - Fotos locais (no próprio tablet):**
1. Toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Pasta Local** → navegue até sua pasta de fotos → **Selecionar**

**Opção B - NAS de casa / PC com Windows (SMB):**
1. Toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Pasta de rede (SMB)**
2. Toque em **"Buscar Rede"** → selecione seu PC/NAS na lista
3. Preencha o nome do compartilhamento + nome de usuário + senha
4. Toque em **Testar Conexão** → **Salvar**

> Configuração completa de SMB: [Conectar a um NAS (SMB)](scenario-smb-setup-pt.md). Isso leva ~5 minutos para configurar uma vez, e depois funciona para sempre.

**Opção C - Google Drive / Dropbox:**
1. Toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **Armazenamento na Nuvem** → escolha o provedor
2. Toque em **Entrar** → complete a autenticação no navegador
3. Selecione a pasta com suas fotos → **Concluído**

![Tela principal do FastMediaSorter - cards de recurso de fotos visíveis depois de adicionar uma pasta de fotos](screenshots/screenshot-pf-step1.png)

---

## Passo 2 - Configure a Pasta para a Apresentação de Slides

Toque e segure na sua pasta de fotos na tela principal → toque em **Editar (ícone de lápis)**.

Defina estas opções:

| Configuração | Valor recomendado | Por quê |
|---------|------------------|-----|
| **Intervalo da Apresentação** | 5-10 segundos | 5 s = sensação de álbum de família animado; 10 s = calmo, bom para fotos artísticas ou grandes grupos onde você quer tempo para reconhecer todo mundo |
| **Incluir Subpastas** | ATIVADO | Mostra fotos de todas as subpastas - ótimo se você organiza por ano/álbum |
| **Modo de ordenação** | Data da Foto (mais recentes primeiro) ou Aleatório | Aleatório = mais variedade diariamente; Data = fotos mais recentes aparecem primeiro |
| **Tipos Suportados** | Somente imagens | Remova Vídeo e Áudio - senão os arquivos de vídeo também vão tocar, interrompendo o fluxo da apresentação |

Toque em **Salvar**.

![Editar Recurso - configurações de Intervalo da Apresentação e Incluir Subpastas](screenshots/screenshot-pf-step2.png)

---

## Passo 3 - (Opcional) Adicione Música de Fundo

Quer uma música suave tocando enquanto vê as fotos? Veja como (precisa de uma edição com áudio - a edição Photos não tem):

1. Primeiro, adicione uma fonte de música: toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → Pasta Local → navegue até sua pasta de música
2. Vá para **Configurações → aba Mídia → Reprodução de áudio, capas e visuais**
3. Ative **"Mostrar fotos aleatórias durante a reprodução de áudio"**

Depois vá para **Configurações → aba Mídia → Imagens, GIFs e apresentação de slides**:
4. Ative **"Tocar música durante a apresentação de slides"**
5. Toque em **"Selecionar Fonte de Música"** → escolha seu recurso de música

> **Dica:** Se a música engasgar quando as fotos vêm de um NAS, use uma pasta de música local para o áudio e deixe apenas as fotos transmitirem da rede - você pode misturar fontes livremente dessa forma.


---

## Passo 4 - Inicie a Apresentação de Slides

1. Toque na sua **pasta de fotos** na tela principal para abri-la
2. Toque em **qualquer foto** para abrir o visualizador em tela cheia
3. Toque em **"Apresentação de slides" <img src="../icons/doc/ic_slideshow.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na barra de ferramentas superior

Pronto - a apresentação de slides roda. As fotos avançam automaticamente no intervalo que você definiu.

> **Início rápido alternativo:** Toque na **zona inferior direita** da tela de foto (a tela é dividida em uma grade 3×3 de zonas de toque invisíveis; inferior direita = zona 9 = REPRODUZIR).


---

## Passo 5 - Mantenha a Tela Ligada

**Este passo é crítico.** O Android economiza bateria desligando a tela depois de alguns minutos - o que arruinaria o porta-retrato. Você precisa desativar isso.

**Opção A - Configuração dentro do app (recomendado):**
Vá para **Configurações → Gerenciamento → Impedir suspensão** e ative.

Isso diz ao Android para manter a tela ligada enquanto o app estiver rodando em primeiro plano. No momento em que você troca de app ou a apresentação de slides para, o tempo limite de tela normal volta.

![Aba Configurações, Gerenciamento - alternador Impedir suspensão ativado](screenshots/screenshot-pf-step5.png)

**Opção B - Configuração do sistema Android:**
Configurações do Android → Tela → Tempo limite da tela → defina para **"Nunca"** (ou o máximo).

> **Também:** Mantenha o tablet **ligado na energia USB** o tempo todo. Um tablet rodando uma apresentação de slides o dia todo vai esgotar a bateria até a noite. Basta usar o carregador original e deixá-lo conectado.

---

## Passo 6 - (Opcional) Adicione um Widget na Tela Inicial

Este passo é para conveniência: quer iniciar o porta-retrato instantaneamente ao pegar o tablet - sem abrir o app e navegar?

1. Toque e segure na sua tela inicial → toque em **Widgets**
2. Encontre **FastMediaSorter** na lista de widgets
3. Arraste o widget **"Atalho de Recurso"** para a sua tela inicial
4. Quando solicitado, selecione seu recurso de fotos
5. Toque no widget a qualquer momento → a apresentação de slides inicia instantaneamente

![Tela inicial do Android com widgets de atalho de recurso do FastMediaSorter colocados](screenshots/screenshot-pf-step6.png)

---

## Pronto! Seu Porta-Retrato Está Funcionando

**Controles enquanto a apresentação de slides está tocando:**
- **Tocar na tela** → pausar / mostrar controles
- **Deslizar para a esquerda / direita** → pular para a próxima / anterior foto manualmente
- **Tocar na zona inferior direita** → parar a apresentação de slides e voltar à lista de arquivos

---

## Dicas

> **Fotos do NAS não atualizam depois de você adicionar novas?** O app armazena a lista de arquivos em cache para velocidade. Para atualizar: volte para a pasta → toque no botão **Atualizar <img src="../icons/doc/ic_refresh.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na barra de ferramentas. As fotos novas aparecem imediatamente.

> **As fotos parecem com zoom ou cortadas?** Abra Configurações → Mídia → Imagens → **"Cortar imagens para preencher a tela"** e experimente as duas posições: DESATIVADO mantém a foto inteira visível, ATIVADO preenche a tela de ponta a ponta (leve corte nas laterais).

> **Celular na vertical usado como porta-retrato?** Ative "Cortar imagens para preencher a tela" para evitar faixas pretas em fotos na horizontal.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| A tela escurece depois de alguns minutos | Ative "Impedir suspensão" em Configurações → Gerenciamento (Passo 5) **e** ligue o carregador USB |
| As fotos não aparecem | Abra as configurações da pasta (Passo 2) e confirme que **Imagens** está marcado em **Tipos Suportados** |
| A música não toca | Verifique se a pasta de música contém pelo menos um arquivo de áudio; confirme que **Tocar música durante a apresentação de slides** está ativado em Configurações → Mídia → Imagens, GIFs e apresentação de slides |
| A apresentação de slides pausa em arquivos de vídeo | Esperado - os vídeos tocam, depois a apresentação continua. Defina "Tipos Suportados → Somente imagens" nas configurações da pasta (Passo 2) para evitar isso |
| Fotos do SMB carregam devagar | Editar pasta → desative "Carregar miniaturas" para reduzir a carga de rede. Ou reduza o intervalo da apresentação para dar mais tempo de carregamento |
| Fotos repetem rápido demais | Aumente o intervalo da apresentação nas configurações da pasta (Passo 2) |

</div>
