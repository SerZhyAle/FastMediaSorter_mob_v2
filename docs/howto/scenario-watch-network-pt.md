---
layout: default
title: "Conecte o Smartwatch a NAS e Compartilhamentos de PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-watch-network-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Conecte o Smartwatch a NAS e Compartilhamentos de PC

> **Nível:** Intermediário &bull; **Tempo:** ~10 minutos &bull; **Dispositivo:** Smartwatch Wear OS

> **Somente na versão completa** - este guia não está implementado na versão distribuída pelo Google Play. Ele se aplica à versão completa, um download direto do APK em [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="scenario-watch-network" dir="/docs/howto/" current="pt" %}

O FastMediaSorter no Wear OS se conecta diretamente ao armazenamento da sua rede doméstica (NAS, pastas compartilhadas de PC, servidores FTP ou SFTP) pelo Wi-Fi. Você pode navegar por arquivos remotos, transmitir música para fones de ouvido Bluetooth e sincronizar suas pastas favoritas sem precisar do seu celular.

> **Novo em compartilhamentos de rede?** Se você ainda não configurou uma pasta compartilhada no seu PC ou NAS, comece primeiro com o nosso guia [Conectar a um NAS / Compartilhamento do Windows (SMB)](scenario-smb-setup-pt.md).

---

## O Que Você Vai Precisar

- Um smartwatch rodando **Wear OS 2.0** ou mais recente conectado à sua rede Wi-Fi de casa
- Uma pasta de rede compartilhada (compartilhamento SMB / Windows, servidor FTP ou servidor SFTP)
- Credenciais de rede: endereço IP ou nome do host, nome do compartilhamento, nome de usuário e senha
- FastMedia Wear instalado no seu relógio

---

## Passo 1 - Abra Recursos no Seu Relógio

1. Abra o **FastMedia Wear** no seu smartwatch.
2. Na tela principal, toque em **Recursos** (ícone de Wi-Fi).
3. A tela de Recursos exibe suas conexões de rede configuradas.

![Tela de Recursos no Wear OS](screenshots/screenshot-wear-network-step1.png)

> **Atalho Sincronizar do Celular:** Se você já adicionou seus compartilhamentos SMB ou SFTP no FastMediaSorter no seu celular Android, toque em **Sincronizar do Celular** para importar todas as configurações de conexão para o seu relógio com um toque.

---

## Passo 2 - Adicione uma Fonte de Rede

1. Na tela de Recursos, toque em **Adicionar recurso**.
2. Selecione seu protocolo de rede:
   - **SMB**: compartilhamentos padrão do Windows, Synology, QNAP ou TrueNAS
   - **FTP**: servidores de arquivo FTP padrão
   - **SFTP**: servidores de transferência de arquivo SSH seguros (suporta senha ou chave SSH privada)
3. Toque em cada campo para digitar os detalhes de conexão usando o teclado na tela do relógio:
   - **Nome**: rótulo opcional (ex.: "NAS de Casa" ou "Compartilhamento de Música")
   - **Endereço do Servidor**: o IP do seu computador ou NAS (ex.: `192.168.1.50`)
   - **Porta**: porta de rede (padrão: 445 para SMB, 21 para FTP, 22 para SFTP)
   - **Nome do Compartilhamento** (somente SMB): o nome da pasta compartilhada no seu NAS/PC
   - **Nome de usuário** e **Senha**: suas credenciais de login

![Tela Adicionar Fonte de Rede no relógio](screenshots/screenshot-wear-network-step2.png)

---

## Passo 3 - Teste e Salve a Conexão

1. Role até a parte inferior do formulário e toque em **Testar**.
2. O FastMedia Wear verifica a rota de rede e as credenciais:
   - Em caso de sucesso, a tela exibe **Conexão bem-sucedida!**.
   - Se houver um problema, uma mensagem de status amigável indica o que ajustar (ex.: endereço do servidor ou senha).
3. Toque em **Salvar** para armazenar a fonte de rede no seu relógio.

![Testar conexão de rede e salvar fonte](screenshots/screenshot-wear-network-step3.png)

---

## Passo 4 - Navegue e Reproduza Mídia de Rede

1. Na tela de Recursos, toque no seu compartilhamento de rede recém-salvo.
2. O FastMedia Wear se conecta ao compartilhamento remoto e lista seu conteúdo.
3. Navegue por pastas e arquivos em modo lista ou grade.
4. Toque em qualquer faixa de áudio para começar a reprodução no player em tela cheia. Para recursos detalhados do player e economia de bateria, veja [Ouça Música no Seu Relógio](scenario-watch-music-pt.md).

![Navegar arquivos e pastas em compartilhamento de rede](screenshots/screenshot-wear-network-step4.png)

---

## Pronto! Recursos de Rede no Wear OS

- **Streaming Independente por Wi-Fi**: Transmite diretamente do seu NAS ou PC pelo Wi-Fi sem retransmissão pelo celular.
- **Suporte a Múltiplos Protocolos**: Suporte completo para SMB, FTP e SFTP com autenticação por senha ou chave privada SSH.
- **Sincronização Bidirecional**: Sincronize conexões do seu companion no celular ou exporte fontes do relógio de volta para o celular.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| O teste de conexão reporta "Falha na conexão" | Verifique se o seu relógio está conectado à mesma rede Wi-Fi que o servidor, e confira o endereço IP |
| Erro de nome de compartilhamento no SMB | Confirme que você digitou apenas o nome do compartilhamento (ex.: `Music`), não o caminho completo com barras |
| Falha na autenticação | Verifique seu nome de usuário e senha. Em compartilhamentos do Windows, confirme que as permissões de compartilhamento de rede permitem sua conta de usuário |
| Carregamento lento pelo Wi-Fi | Confirme que o sinal Wi-Fi do relógio está forte e que o roteamento de rede 5 GHz / 2,4 GHz para o servidor local não está bloqueado |

</div>
