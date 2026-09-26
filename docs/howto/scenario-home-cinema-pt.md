---
layout: default
title: "Cinema em Casa e Streaming em VR - FastMediaSorter v2"
permalink: /docs/howto/scenario-home-cinema-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 🍿 Cinema em Casa e Streaming em VR

> **Nível:** Iniciante &bull; **Tempo:** ~15 minutos &bull; **Edição:** Standard, Legacy, VR, noLegal (Lite não tem fontes de rede, Photos não tem vídeo)

{% include lang-switcher.html doc="scenario-home-cinema" dir="/docs/howto/" current="pt" %}

Assista à sua coleção de séries diretamente do seu PC de casa - no celular, tablet ou headset de VR baseado em Android (Meta Quest, Pico). Sem copiar arquivos. Sem cabos USB. Basta apertar o play.

> **Como isso funciona?** Seu celular e PC estão no mesmo Wi-Fi de casa. O app se conecta à pasta compartilhada do seu PC e transmite o vídeo diretamente - assim como a Netflix transmite dos servidores deles, mas usando sua própria rede doméstica. O arquivo de vídeo nunca é baixado para o celular; ele é reproduzido em tempo real.

---

## O Que Você Vai Precisar

- Celular / tablet / headset de VR na mesma **rede Wi-Fi de casa** que o seu PC
- Vídeos no seu **PC ou NAS** (a caixa do seu roteador com armazenamento)
- FastMediaSorter instalado

---

## Passo 1 - Compartilhe Sua Pasta de Vídeos no PC

Primeiro, torne a pasta de vídeos acessível pela sua rede doméstica.

No **Windows:**
1. Abra o **Explorador de Arquivos**, navegue até sua pasta de vídeos (ex.: `D:\Series`)
2. **Clique com o botão direito** na pasta → **Propriedades** → aba **Compartilhamento** → clique em **Compartilhar..**
3. No menu suspenso escolha **Everyone** (ou seu nome de usuário) → clique em **Adicionar** → clique em **Compartilhar**
4. Anote o endereço IP do seu PC - você vai precisar dele no Passo 2

> **Como encontrar o IP do seu PC:** pressione **Win + R**, digite `cmd`, pressione Enter. Digite `ipconfig` e pressione Enter. Encontre a linha **IPv4 Address** sob o seu adaptador Wi-Fi. Exemplo: `192.168.1.100`.

---

## Passo 2 - Adicione a Pasta de Vídeos no FastMediaSorter

1. Abra o app → toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → **"Pasta de rede (SMB)"**
2. Toque em **"Buscar Rede"** - o app procura por PCs disponíveis na sua rede doméstica
3. Quando o seu PC aparecer na lista, toque nele - o endereço é preenchido automaticamente
4. Digite o nome do compartilhamento (o nome da pasta de vídeos), o nome de usuário e a senha do Windows
5. Toque em **Testar Conexão** → **Salvar**

> **Não encontrou o seu PC pela busca?** Digite o endereço manualmente: `\\192.168.1.100\Series` (substitua pelo seu IP e nome de pasta). Veja o [Guia de Configuração SMB](scenario-smb-setup-pt.md) completo para todos os cenários de conexão.


---

## Passo 3 - Abra a Pasta de Vídeos

Toque no recurso que você acabou de adicionar na tela principal.

Suas pastas de séries e arquivos de vídeo aparecem em uma grade com miniaturas - assim como navegar localmente.

![Pasta de vídeos SMB - arquivos de episódios (MKV) listados por nome de arquivo](screenshots/screenshot-hc-step3.png)

---

## Passo 4 - Configure o Próximo Episódio Automático

Para que o próximo episódio comece automaticamente quando um termina - sem precisar escolher o próximo manualmente:

1. Volte para a tela principal → **toque e segure** no seu recurso de vídeo → toque em **Editar**
2. Defina **Tipos Suportados** → **Somente vídeo** (oculta arquivos que não são vídeo)
3. Defina **Modo de ordenação** → **Nome (A→Z)** - isso garante que os episódios toquem em ordem (Episódio 1, 2, 3..)
4. Toque em **Salvar**

Depois inicie a apresentação de slides no player (o comando **Apresentação de slides**) e ative **Configurações → Player → Reproduzir vídeo/áudio na apresentação até o fim** - cada episódio então toca até o fim antes que o próximo comece.

> **Por que ordenar por nome?** Os arquivos de episódios geralmente são nomeados `S01E01`, `S01E02`, etc. Ordenar por nome os coloca automaticamente na ordem correta dos episódios.

---

## Passo 5 - Comece a Assistir

1. Abra a pasta, navegue até a subpasta da série
2. Toque no **Episódio 1** - o player de vídeo abre imediatamente e começa a transmitir
3. O vídeo toca pelo Wi-Fi - sem esperar por downloads

![Player de vídeo em tela cheia - episódio tocando com barra de progresso](screenshots/screenshot-hc-step5.png)

---

## Passo 6 - Controles Durante a Reprodução

**Gestos de toque durante a reprodução:**
- **Deslizar para a esquerda** → pular para o próximo episódio
- **Deslizar para a direita** → voltar para o episódio anterior
- **Tocar na tela** → mostrar / ocultar controles
- **Pinça** → aproximar ou afastar o zoom (útil para filmes widescreen em um celular na vertical)
- **Toque duplo na borda esquerda / direita** → retroceder / avançar 10 segundos

Quando o **"Próximo automático"** está ativado, o próximo episódio começa automaticamente quando o atual termina - assim como a Netflix.

---

## Passo 7 - Para Headsets de VR (Meta Quest, Pico)

> **Esta seção é para pessoas com um headset de VR (como Meta Quest 2/3 ou Pico 4).** Se você não tem um, pule este passo.

Headsets de VR baseados em Android podem rodar o FastMediaSorter. Instale-o via sideload:
1. Baixe o APK da [página de Downloads](../DOWNLOADS.md)
2. No seu headset, ative **"Instalar de fontes desconhecidas"** nas configurações de Desenvolvedor
3. Instale o APK usando o SideQuest ou diretamente via ADB

Depois de instalado, o player de vídeo funciona exatamente da mesma forma:
- O vídeo preenche a **tela plana virtual** dentro do headset
- Use o **gatilho do controle** para tocar nos botões
- Use o **thumbstick** para deslizar entre episódios (se o seu headset mapear botões de mídia)
- Para filmes e séries 2D comuns - funciona imediatamente, sem configuração extra

> **Para experiência de cinema em VR:** você pode usar um app dedicado de cinema em VR como launcher, e então escolher "Abrir com FastMediaSorter" para o gerenciamento de arquivos. O FastMediaSorter cuida da navegação de arquivos; o app de cinema em VR cuida da exibição imersiva 360°.

---

## Pronto! O Que Tentar a Seguir

- Adicione um recurso do **Google Drive** ou **Dropbox** para filmes armazenados na nuvem - funciona da mesma forma
- Use os **Favoritos** (toque no botão de estrela <img src="../icons/doc/ic_star_filled.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> enquanto assiste) para marcar a série que você está "assistindo no momento" - volte a ela a qualquer momento
- **Legendas:** se sua pasta de vídeos tiver arquivos de legenda `.srt` correspondentes ao lado dos arquivos de vídeo, toque no **botão CC / legendas** na barra de ferramentas do player para ativá-las
- **Rádio pela internet ou streams ao vivo:** se você também quiser adicionar estações de rádio pela internet ou fontes RTSP/HLS, veja o guia [Rádio pela Internet e Streams](scenario-internet-radio-pt.md) - não precisa de NAS ou PC, apenas uma conexão de rede.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| O vídeo engasga ou fica em buffer | Execute um **Teste de Velocidade**: toque e segure no recurso → Editar → Teste de Velocidade. Se a velocidade estiver abaixo de 5 Mbps, tente mudar seu celular para a **banda Wi-Fi de 5 GHz** (mais rápida, mas com alcance menor) |
| O vídeo não toca (erro de formato) | No player, toque em **Opções <img src="../icons/doc/ic_more_vert.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** → mude o **Decodificador** de Hardware para Software (mais lento, mas mais compatível) |
| Os episódios tocam na ordem errada | Confirme que o Modo de ordenação está definido como **Nome (A→Z)** nas configurações de Editar da pasta |
| O próximo automático não inicia | Confirme que a apresentação de slides está em execução e que Configurações → Player → Reproduzir vídeo/áudio na apresentação até o fim está ativado |
| O headset de VR não consegue instalar o APK | Abra as configurações de Desenvolvedor do headset e ative "Permitir instalações de fontes desconhecidas" |

</div>
