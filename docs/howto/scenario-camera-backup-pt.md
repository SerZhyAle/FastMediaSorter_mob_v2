---
layout: default
title: "Backup Agendado da Câmera para o PC - FastMediaSorter v2"
permalink: /docs/howto/scenario-camera-backup-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 📷 Backup Agendado da Câmera para o PC

> **Nível:** Iniciante &bull; **Tempo:** ~15 minutos de configuração &bull; **Edição:** Standard, Photos, Legacy, VR, noLegal (precisa de fontes de rede - Lite não tem)

{% include lang-switcher.html doc="scenario-camera-backup" dir="/docs/howto/" current="pt" %}

Copie automaticamente as novas fotos da câmera do seu celular para o seu computador de casa **todas as noites, pelo Wi-Fi**. Configure uma vez - e funciona para sempre, sem nenhuma ação manual.

**O que isso te dá:** toda manhã você acorda e as fotos da noite anterior já estão no seu PC. Sem cabos. Sem assinaturas na nuvem. Sem esquecer. Totalmente automático.

---

## O Que Você Vai Precisar

- Celular e PC conectados ao **mesmo Wi-Fi de casa** (mesmo roteador)
- Uma pasta no seu PC onde as fotos serão salvas (ex.: `C:\PhoneBackup`)
- FastMediaSorter instalado em uma edição com fontes de rede (**Standard**, Photos, Legacy, VR, noLegal)

> **Não sabe qual edição você tem?** Abra **Configurações** <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> → **Informações do sistema**. A linha **Edição** informa a edição (Standard / Lite / etc.).

---

## Passo 1 - Crie uma Pasta de Backup no Seu PC

Primeiro, crie uma pasta no seu PC. Depois compartilhe-a para que o celular possa acessá-la.

No **Windows:**
1. Crie uma nova pasta em qualquer lugar - por exemplo `C:\PhoneBackup`
2. **Clique com o botão direito** na pasta → **Propriedades** → aba **Compartilhamento** → clique em **Compartilhar..**
3. No menu suspenso, selecione seu nome de usuário ou digite **Everyone** → clique em **Adicionar** → clique em **Compartilhar**
4. O Windows mostra o caminho de rede - anote-o. Ele se parece com: `\\MYPC\PhoneBackup`

> **Anote também o endereço IP do seu PC** - você vai precisar dele no Passo 2. O jeito mais rápido: pressione **Win + R**, digite `cmd`, pressione Enter. Na janela preta digite `ipconfig` e pressione Enter. Encontre a linha **IPv4 Address** sob o seu adaptador Wi-Fi. Exemplo: `192.168.1.100`. Anote esse número.

---

## Passo 2 - Conecte o App à Pasta do Seu PC

Agora informe ao FastMediaSorter para onde enviar as fotos.

> **O que é SMB?** É simplesmente a forma como o Windows compartilha pastas pelo Wi-Fi de casa. Você não precisa entender os detalhes - só siga os passos.

1. Abra o app → toque em **Adicionar <img src="../icons/doc/ic_add.png" alt="" width="18" height="18" style="vertical-align:text-bottom">** na barra de ferramentas superior → selecione **"Pasta de rede (SMB)"**
2. No campo **Servidor / Caminho**, digite: `\\192.168.1.100\PhoneBackup`
   - Substitua `192.168.1.100` pelo IP real do seu PC obtido no Passo 1
   - Substitua `PhoneBackup` pelo nome real da sua pasta
3. Digite seu **nome de usuário** e **senha** do Windows (os mesmos que você usa para entrar no seu PC)
4. Toque em **Testar Conexão** - espere alguns segundos - você deve ver uma mensagem verde de sucesso
5. Toque em **Salvar**

> **Não consegue conectar?** Confira o [Guia de Configuração SMB](scenario-smb-setup-pt.md) - ele cobre todos os problemas comuns de conexão com soluções passo a passo.


---

## Passo 3 - Abra as Configurações de Operações Agendadas

1. Toque em **Configurações** (o ícone de engrenagem <img src="../icons/doc/ic_settings.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> na barra de ferramentas)
2. Vá para a aba **Gerenciamento**
3. Role até **"Operações agendadas por horário"** e toque em **"Operações de arquivo agendadas"** - a tela de operações agendadas se abre
4. Ative **"Usar operações agendadas"** no topo dessa tela

![Configurações → Gerenciamento - seção Agendado com botão ADICIONAR](screenshots/screenshot-cb-step3.png)

---

## Passo 4 - Crie um Novo Agendamento

Toque no botão **+** (**"Adicionar"**) na tela de operações agendadas.

Uma nova caixa de diálogo de agendamento se abre.

![Caixa de diálogo Adicionar Agendamento - seção Condições: opções de intervalo e sobrescrita](screenshots/screenshot-cb-step4.png)

---

## Passo 5 - Preencha o Agendamento de Backup

Preencha cada campo:

| Campo | O que definir | Exemplo |
|-------|------------|---------|
| **Nome** | Qualquer rótulo para identificar este agendamento | `Nightly Camera Backup` |
| **Origem** | Onde estão as fotos da sua câmera | Selecione **"Fotos da Câmera"** - ele encontra todas as fotos da câmera automaticamente |
| **Destino** | Sua pasta de backup no PC | Selecione o recurso SMB que você acabou de adicionar (`PhoneBackup (SMB)`) |
| **Operação** | O que fazer com os arquivos | **"Copiar (ignorar existentes)"** - copia apenas fotos novas, nunca duplica |
| **Agendamento** | Quando executar | `Daily at 02:00` - executa enquanto você dorme |
| **Executar apenas no Wi-Fi** | Ative esta opção | Evita que o backup use seus dados móveis por acidente |

> **O que é "Fotos da Câmera"?** É uma pasta virtual especial que o FastMediaSorter cria automaticamente. Ela sempre mostra todas as fotos tiradas pela sua câmera - mesmo que estejam armazenadas em pastas diferentes no seu celular. Prefira sempre essa opção em vez de escolher um caminho manual.

![Adicionar Agendamento - Origem: Fotos da Câmera, Operação: Copiar, Destino: SMB](screenshots/screenshot-cb-step5.png)

---

## Passo 6 - Salve e Permita Acesso em Segundo Plano

Toque em **Salvar**.

O agendamento aparece na lista - agora está ativo.

**Você pode ver uma caixa de diálogo de permissão.** O app pede para ser excluído da economia de bateria. Toque em **"Desativar Otimização"** (ou **"Permitir"**).

> **Por que este passo é importante?** O Android tenta economizar bateria interrompendo automaticamente apps que rodam em segundo plano. Sem essa permissão, o Android pode interromper o backup no meio da noite. Conceder isso apenas permite que o app acorde no horário agendado - não consome sua bateria de forma perceptível.

![Entrada de agendamento salva: Fotos da Câmera → SMB no horário agendado](screenshots/screenshot-cb-step6.png)

---

## Passo 7 - Teste Agora Mesmo

Não espere até as 2h da manhã - teste o backup imediatamente para garantir que tudo funciona:

1. Vá para **Configurações → Gerenciamento → Operações de arquivo agendadas**
2. Encontre seu agendamento → toque em **"Executar agora"**
3. Uma notificação aparece no topo da tela mostrando o progresso da transferência
4. Quando terminar: toque no recurso SMB (`PhoneBackup`) → suas fotos da câmera devem estar visíveis ali

> **Nada foi copiado?** Se todas as suas fotos já estão na pasta de backup (ou a pasta da câmera do celular está vazia), o app corretamente copia zero arquivos. Tire uma nova foto de teste e execute novamente.


---

## Pronto! Veja o Que Acontece Todas as Noites

1. Às 02:00 o app acorda silenciosamente
2. Ele verifica a pasta da sua câmera e a compara com a pasta de backup do PC
3. Copia apenas as fotos que ainda não estão no PC - leva de alguns segundos a alguns minutos
4. Mostra uma notificação: "12 arquivos copiados" (ou quantos forem novos)
5. Volta a dormir

Seu PC recebe fotos novas toda manhã. Você nunca precisa pensar nisso.

---

## Dicas

> **Quer liberar espaço no celular depois do backup?** Mude a Operação para **"Mover"** em vez de "Copiar". As fotos são excluídas do celular assim que são copiadas com segurança para o PC. Use isso com cuidado - uma vez movidas, as fotos não ficam mais no celular.

> **Tem vários celulares na família?** Crie um agendamento para cada celular. Use subpastas diferentes como destino - por exemplo `PhoneBackup\Mom` e `PhoneBackup\Dad` - para que todos os dispositivos façam backup no mesmo PC sem misturar arquivos.

> **Prefere fazer backup no Google Drive?** Adicione um recurso do Google Drive como destino em vez de SMB - o restante dos passos é idêntico.

---

## Solução de Problemas

| Problema | O que tentar |
|---------|------------|
| O agendamento não roda à noite | Vá para **Configurações do Android → Apps → FastMediaSorter → Bateria** → defina como **Sem restrições** |
| Erro "Destino inacessível" | Seu celular precisa estar no Wi-Fi na hora do backup. Se o Wi-Fi estava desligado às 2h, o backup é pulado e tentado novamente automaticamente na noite seguinte |
| Algumas fotos não foram salvas no backup | Use o recurso virtual **"Fotos da Câmera"** como origem - ele captura fotos de todas as pastas de câmera no seu celular |
| Arquivos duplicados aparecendo no PC | Confirme que a Operação está definida como **"Copiar (ignorar existentes)"**, não "Copiar (sobrescrever)" |

</div>
