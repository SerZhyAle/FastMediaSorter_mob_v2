---
layout: default
title: "Abra as Pastas do Seu PC Escaneando um Único Código - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_sftp.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Abra as Pastas do Seu PC Escaneando um Único Código

> **Nível:** Iniciante &bull; **Edição:** Standard, Photos, Legacy, VR, noLegal (Lite não tem fontes de rede; o escaneamento precisa de câmera, o método por arquivo funciona em qualquer lugar)

{% include lang-switcher.html doc="scenario-companion-share" dir="/docs/howto/" current="pt" %}

Você executa um pequeno programa auxiliar no seu PC Windows, escolhe as pastas com seus vídeos, músicas, documentos ou fotos, e ele mostra um código na tela. No celular você toca em **Adicionar**, aponta a câmera para esse código, e as pastas do PC são conectadas instantaneamente - sem digitar endereço, sem porta, sem senha, sem cabos.

> **Explicação em linguagem simples:** O auxiliar do Windows transforma as pastas que você escolheu em um compartilhamento privado e somente leitura no seu Wi-Fi de casa, e imprime um código que já contém tudo que o celular precisa para acessá-las. Escanear esse código é o mesmo que preencher um longo formulário de conexão à mão - o celular apenas o lê em uma olhada. Os arquivos então abrem sob demanda, transmitidos pelo Wi-Fi; nada é copiado para o celular até que você peça.

---

## O Programa Auxiliar

O "companion" é um recurso integrado do **[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)** (antigo FastMediaSorter LITE) - o organizador de mídia gratuito para Windows do mesmo autor. Ao compartilhar pastas com ele, o programa:

- Inicia um servidor SFTP privado apenas para essas pastas no seu PC.
- Gera suas próprias chaves e configura a inicialização automática, para que o compartilhamento esteja lá na próxima vez também.
- Mostra um **código QR** na tela e também pode salvar um pequeno arquivo de configuração `.fmscfg`.

**Onde conseguir:**

- Site: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Publicando pastas (passo a passo): [Como publicar pastas do PC no Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [última versão](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (instalador ou ZIP portátil)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: procure por "FastMediaSorter LITE" (ainda listado com o nome antigo)

---

## O Que Você Vai Precisar

- Um PC com Windows com o **Fast Media Sorter for Windows** instalado
- Seu celular e PC na **mesma rede Wi-Fi** (mesmo roteador)
- Para o caminho mais rápido: uma **câmera** no celular para escanear o código (um caminho por arquivo está disponível se não houver câmera)

---

## Passo 1 - Compartilhe as Pastas no PC

1. Instale e execute o **Fast Media Sorter for Windows**, depois abra a aba **Compartilhar** nas configurações.
2. Escolha a(s) pasta(s) que você quer no celular - Filmes, Músicas, Documentos, Fotos, qualquer coisa.
3. O app inicia o servidor SFTP, gera as chaves e configura a inicialização automática sozinho. Nada mais a configurar.
4. Agora ele mostra um **código QR** na tela do PC. Deixe essa janela aberta para o Passo 2.

> Prefere um arquivo em vez de um código? Use **Salvar .fmscfg** na mesma janela e envie esse arquivo para o celular (e-mail, Telegram, ou qualquer pasta compartilhada). Veja [Passo 2, Método B](#step-2-method-b---import-the-file).

---

## Passo 2, Método A - Escaneie o Código (mais rápido)

1. Abra o FastMediaSorter e toque no botão **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na tela principal.
2. Toque em **"Importar por código de barras"** - ele fica ao lado dos quatro cards de tipo de recurso (Local, SMB, SFTP/FTP, Nuvem) e no cabeçalho do formulário SFTP.
3. A câmera se abre com a dica *"Aponte a câmera para o código QR do companion"*. Aponte o celular para o QR no seu PC. Em um ambiente escuro, toque em **Lanterna**.
4. Uma confirmação aparece - *"Importar acesso - Adicionar o recurso SFTP .. com N pasta(s)?"*. Toque em **Importar**.
5. Pronto. Um recurso somente leitura por pasta compartilhada aparece na tela principal, com a chave do servidor fixada automaticamente.

> A opção **Importar por código de barras** fica oculta em dispositivos sem câmera e em headsets de VR - use o Método B nesses casos.

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## Passo 2, Método B - Importar o Arquivo {#step-2-method-b---import-the-file}

Use isso quando o celular não tem câmera, ou quando o PC e o celular não estão lado a lado.

1. No PC, use **Salvar .fmscfg** e leve o arquivo até o celular (e-mail, Telegram, nuvem ou uma pasta compartilhada).
2. **Se o arquivo já está no celular:** toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** -> **"SFTP / FTP"** -> **"Importar de arquivo"**, depois escolha o arquivo `.fmscfg`.
3. **Se você o recebeu como anexo** (Telegram ou e-mail): basta tocar no anexo `.fmscfg` - o app abre uma caixa de diálogo de confirmação diretamente.
4. Confirme a mesma caixa de diálogo *"Importar acesso"* e toque em **Importar**. Os recursos somente leitura aparecem.

> **Trate o código e o arquivo como uma chave.** Ambos incorporam a senha de acesso para que o celular possa se conectar sem digitar nada. Não publique a captura de tela do QR nem o arquivo `.fmscfg` publicamente.

---

## Pronto! Agora Você Pode..

As pastas compartilhadas se comportam como qualquer outro recurso no app. Por exemplo:

- **Assista a filmes e séries** do PC no seu celular, tablet ou TV box Android - transmitido, nada copiado. Veja [Cinema em Casa e Streaming em VR](scenario-home-cinema-pt.md).
- **Toque sua biblioteca de música** em qualquer lugar ou em um rádio automotivo.
- **Leia PDFs e EPUBs** armazenados no PC, mantendo sua última posição.
- **Navegue por um arquivo de fotos** e organize-o com a Classificação Rápida, ou exiba-o como um [porta-retrato digital](scenario-photo-frame-pt.md).
- **Envie um arquivo para um app especializado** - abra a folha de Informações de um arquivo de rede e toque em Baixar e Abrir.
- **Copie ou mova arquivos** entre o PC e o celular em qualquer direção.

---

## Como Funciona (por trás dos panos)

- O auxiliar do Windows executa um **servidor SFTP** leve vinculado às pastas que você escolheu, apenas na sua rede local.
- O código QR (ou arquivo `.fmscfg`) codifica a conexão: host, porta, credencial, os caminhos das pastas compartilhadas e a impressão digital da chave do servidor. Compartilhamentos densos são enviados compactados, então até muitas pastas cabem em um único código.
- O celular lê essa carga de dados, verifica-a e cria um **recurso SFTP somente leitura por pasta**. O código também carrega a impressão digital da chave do servidor do PC, e o celular a verifica em cada conexão - navegação, cópia, miniaturas e reprodução. Se outro computador algum dia responder no lugar do seu PC, o celular não carrega nada e avisa que o servidor parece diferente.
- Por ser sua rede Wi-Fi local e somente leitura, o celular navega e transmite os arquivos sem alterar nada no PC.
- **Na mesma rede Wi-Fi, o celular encontra o PC sozinho.** O companion anuncia o compartilhamento na rede local, e o celular o identifica pela chave fixada - então mesmo que o endereço do PC na rede mude, o compartilhamento continua funcionando sem escanear novamente.
- **Uma importação pode funcionar em casa e fora.** O código pode carregar mais de um endereço - o local, um IPv6 e um redirecionamento de porta pela internet. O celular tenta cada um e usa o que estiver acessível no momento: o endereço local em casa, o da internet nos dados móveis. O mesmo recurso continua funcionando enquanto você se move entre redes, desde que o PC esteja de fato acessível de onde você está.
- **Se não conseguir se conectar, o app explica o que fazer** - entrar na mesma rede Wi-Fi, ou configurar o acesso no PC - em vez de um erro seco. Quando o companion inclui uma observação sobre acesso, o celular a exibe.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| Nenhuma opção "Importar por código de barras" | O dispositivo não tem câmera, ou é uma versão VR. Use o [Método B - Importar o Arquivo](#step-2-method-b---import-the-file) |
| A câmera diz que o acesso é necessário | Conceda a permissão de câmera quando solicitado - ela é usada apenas para o escaneamento |
| "Este arquivo não é uma configuração válida do companion" | O código ou arquivo não é do companion do Windows. Reexporte-o na aba **Compartilhar** |
| "Criado por uma versão mais recente do companion" | Atualize o FastMediaSorter no celular, ou reexporte de uma versão compatível do companion |
| Recurso adicionado, mas as pastas estão vazias | Confirme que o auxiliar do PC ainda está em execução. Na **mesma rede Wi-Fi** o app encontra o PC sozinho; se ainda falhar, o app mostra o que verificar |
| Funciona no Wi-Fi mas não nos dados móveis | Para alcançar o PC de outra rede, ele precisa estar acessível pela internet - configure o redirecionamento de porta ou IPv6 nas configurações **Compartilhar** do companion. Sem isso, o compartilhamento funciona apenas na mesma rede Wi-Fi |

→ Mais ajuda: [TROUBLESHOOTING-pt.md](../TROUBLESHOOTING-pt.md) &bull; Fundamentos: [Conectar a um NAS / Compartilhamento do Windows (SMB)](scenario-smb-setup-pt.md)

</div>
