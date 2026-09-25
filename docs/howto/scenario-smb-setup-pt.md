---
layout: default
title: "Conectar a um NAS / Compartilhamento do Windows (SMB) - FastMediaSorter v2"
permalink: /docs/howto/scenario-smb-setup-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 🖥️ Conectar a um NAS de Casa / Compartilhamento do Windows (SMB)

> **Nível:** Iniciante &bull; **Edição:** Standard, Photos, Legacy, VR, noLegal (Lite não tem fontes de rede)

{% include lang-switcher.html doc="scenario-smb-setup" dir="/docs/howto/" current="pt" %}

O SMB (também chamado de Compartilhamento de Arquivos do Windows ou CIFS) permite navegar pelos arquivos do seu PC, notebook ou dispositivo NAS de casa exatamente como se estivessem no seu celular - sem cabos, sem USB, apenas Wi-Fi.

> **Explicação em linguagem simples:** Imagine que o seu PC tem um quadro de avisos público no seu Wi-Fi de casa. Qualquer dispositivo na casa pode ler esse quadro. O FastMediaSorter se conecta a esse "quadro" (sua pasta compartilhada) e permite navegar pelos seus arquivos como se estivessem armazenados diretamente no seu celular. Nada é copiado ou baixado de antemão - os arquivos abrem sob demanda.

---

## O Que Você Vai Precisar

- Seu celular e seu PC / NAS na **mesma rede Wi-Fi** (mesmo roteador)
- O **endereço IP** do seu PC ou NAS (ex.: `192.168.1.100`)
- O **nome do compartilhamento** (o nome da pasta que você compartilhou, ex.: `Photos`)
- Um **nome de usuário e senha** para esse compartilhamento (ou acesso convidado, se ativado)

> **Não sabe os termos?** Sem problema - os Passos 1 e 2 explicam exatamente onde encontrar essas informações.

---

## Passo 1 - Encontre o Endereço IP do Seu PC

O endereço IP é o "endereço residencial" do seu PC na sua rede Wi-Fi. Você precisa dele para que o celular saiba onde procurar.

No **Windows:**
1. Pressione `Win + R`, digite `cmd`, pressione Enter - uma janela de texto preta se abre
2. Digite `ipconfig` e pressione Enter
3. Procure por **IPv4 Address** sob o seu adaptador Wi-Fi - algo como `192.168.1.100`

> A linha que você precisa está rotulada **"IPv4 Address"** (não IPv6, que se parece com uma longa série de letras e números). Ela deve começar com `192.168.` na maioria das redes domésticas.

Em um **NAS** (Synology, QNAP, etc.):
- Abra o painel web do NAS → Configurações de rede - o IP é mostrado lá

> Anote o IP - você vai precisar dele no Passo 6.

![PowerShell do Windows - saída do ipconfig, IPv4 Address `192.168.1.100` visível](screenshots/screenshot-smb-step1.png)

---

## Passo 2 - Encontre o Nome do Compartilhamento no Seu PC

O "nome do compartilhamento" é o nome público da sua pasta na rede. Pode ser o mesmo que o nome da pasta, ou diferente.

No **Windows:**
1. Abra o **Explorador de Arquivos**
2. Clique com o botão direito na pasta que você quer compartilhar → **Propriedades**
3. Vá para a aba **Compartilhamento**
4. Olhe em **Caminho de Rede** - parece com `\\DESKTOP-ABC\Photos`
5. A parte depois da última `\` é o seu **nome do compartilhamento** (aqui: `Photos`)

> **A pasta ainda não está compartilhada?** Clique em **Compartilhar..** → escolha **Everyone** → **Adicionar** → **Compartilhar**. O Windows vai mostrar o caminho de rede imediatamente.

> **Importante:** confirme que **Descoberta de Rede** e **Compartilhamento de Arquivos** estão ativados no Windows. Vá em Painel de Controle → Central de Rede e Compartilhamento → Alterar configurações avançadas de compartilhamento → ative "Descoberta de rede" e "Compartilhamento de arquivos e impressoras".

![Propriedades de pasta do Windows - aba Compartilhamento, Caminho de Rede `\\MARK\Common` visível](screenshots/screenshot-smb-step2.png)

---

## Passo 3 - Abra o FastMediaSorter e Toque em "+"

1. Abra o app
2. Na **tela principal**, toque no botão **"Adicionar" <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na barra de ferramentas superior

![Tela principal do FastMediaSorter - botão Adicionar destacado na barra de ferramentas superior, aba SMB visível](screenshots/screenshot-smb-step3.png)

---

## Passo 4 - Selecione "Pasta de rede (SMB)"

Na lista de tipos de recurso, toque em **"Pasta de rede (SMB)"** (ou na aba SMB).

![Caixa de diálogo Selecionar Tipo de Pasta - quatro opções: Pasta Local, Pasta de Rede (SMB), SFTP/FTP, Armazenamento na Nuvem](screenshots/screenshot-smb-step4.png)

---

## Passo 5 - Tente Primeiro a Descoberta Automática

Toque no botão **"Buscar Rede"**. O app vai procurar no seu Wi-Fi local por dispositivos com compartilhamentos SMB.

- Espere ~10 segundos
- Uma lista de dispositivos encontrados aparece
- Toque no seu PC ou NAS - o endereço IP é preenchido automaticamente

<!-- TODO screenshot: Scan Network in progress - spinner or "Scanning.." text -->

<!-- TODO screenshot: Scan results list showing one or more found devices -->

> **Não encontrou nada?** Sem problema - pule para o Passo 6 e digite o IP manualmente. Isso acontece quando seu roteador usa Isolamento de AP (uma configuração que bloqueia a comunicação celular-PC por segurança). O IP manual sempre funciona.

---

## Passo 6 - Preencha os Detalhes de Conexão

Preencha o formulário:

| Campo | O que digitar | Exemplo |
|-------|--------------|---------|
| **Servidor / Caminho** | `\\IP\ShareName` | `\\192.168.1.100\Photos` |
| **Nome de usuário** | Seu nome de login do Windows | `john` |
| **Senha** | Sua senha do Windows | `••••` |
| **Nome de Exibição** | Qualquer nome que você quiser (opcional) | `PC de Casa - Fotos` |

> **Usa uma conta Microsoft (e-mail) para entrar no Windows?** Use seu **endereço de e-mail completo** como nome de usuário (ex.: `john@outlook.com`), não apenas o primeiro nome. Sua senha é a mesma que você digita para desbloquear seu PC.

> **Não tem senha, ou está usando Convidado?** Tente deixar o Nome de usuário e a Senha em branco e toque em Testar Conexão - alguns PCs domésticos permitem acesso aberto.

![Adicionar Pasta de Rede (SMB) - IP do Servidor `192.168.1.100`, NomeDoCompartilhamento e credenciais preenchidos](screenshots/screenshot-smb-step6.png)

![Adicionar Pasta de Rede (SMB) - seção inferior: opções, tipos de mídia, botão ADICIONAR ESTE RECURSO](screenshots/screenshot-smb-step6b.png)

**Referência de formato de endereço:**

| Formato | Exemplo |
|--------|---------|
| Windows padrão | `\\192.168.1.100\Photos` |
| Estilo Linux / macOS | `smb://192.168.1.100/Photos` |
| Subpasta | `\\192.168.1.100\Media\Movies` |
| Porta personalizada | `smb://192.168.1.100:445/Photos` |

---

## Passo 7 - Teste a Conexão

Toque em **"Testar Conexão"**.

- **Mensagem verde** = sucesso → vá para o Passo 8! Você está quase terminando.
- **Mensagem vermelha** = algo está errado → verifique a tabela de Solução de Problemas abaixo. Correção mais comum: confira novamente o IP e o nome do compartilhamento.

<!-- TODO screenshot: Green "Connection successful" toast or inline success message -->

---

## Passo 8 - Salve e Abra

Toque em **"Salvar"**. A nova pasta aparece na tela principal com um selo SMB.

Toque nela para navegar pelo conteúdo - fotos, vídeos e outros arquivos aparecem como miniaturas, igual a qualquer pasta local.

![Tela principal do FastMediaSorter - novo card de recurso SMB "Common" (smb://192.168.1.100/Common) com selo Pasta de rede SMB destacado](screenshots/screenshot-smb-step8.png)

---

## Pronto! Agora Você Pode..

- Navegar por todos os arquivos do seu PC pelo celular
- Tocar vídeos e músicas diretamente - sem precisar baixar
- Copiar ou mover arquivos entre o celular e o PC
- Usar essa pasta como fonte para apresentação de slides, porta-retrato ou música no carro

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| "Conexão recusada" | Abra o Firewall do Windows → permita a **porta TCP 445** de entrada. Ou desative temporariamente o firewall para testar |
| "Senha incorreta" | Tente deixar o **Nome de usuário em branco** (acesso convidado). Ou, se você usa uma conta Microsoft, digite seu **e-mail completo** como nome de usuário |
| "Host não encontrado" | Confirme que o celular e o PC estão na **mesma rede Wi-Fi** e no mesmo roteador. O Isolamento de AP (uma configuração de segurança do roteador) pode bloquear isso - tente desativá-lo nas configurações do roteador |
| A busca não encontra nada | Desative a VPN no celular. Verifique também se a **Descoberta de Rede** está ativada no Windows (Painel de Controle → Central de Rede e Compartilhamento). Depois tente digitar o IP manualmente |
| Navegação muito lenta | Toque em **Editar** no recurso → execute o **Teste de Velocidade** para ver a taxa de transferência real. Desative as miniaturas de vídeo para conexões lentas |
| Funciona no Wi-Fi mas não nos dados móveis | Esperado - o SMB é um protocolo de rede local apenas. Não pode funcionar com dados móveis |

→ Mais ajuda: [TROUBLESHOOTING-pt.md](../TROUBLESHOOTING-pt.md)

</div>
