---
layout: default
title: "🔧 Guia de Solução de Problemas"
permalink: /docs/TROUBLESHOOTING-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# 🔧 Guia de Solução de Problemas

Guia atual de solução de problemas do FastMediaSorter v2. Use a grade canônica de versões em [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) quando o problema depender do caminho de build selecionado (Standard, Lite, Photos, Legacy ou XR / noLegal).

{% include lang-switcher.html doc="TROUBLESHOOTING" dir="/docs/" current="pt" %}

---

## Problemas de Conexão

### ❌ "Não é possível conectar ao servidor SMB"

**Possíveis causas:**
1. **Rede errada** - O telefone precisa estar no mesmo Wi-Fi que o NAS
2. **Formato de endereço errado** - Tente os dois formatos:
   - `\\192.168.1.100\share`
   - `smb://192.168.1.100/share`
3. **Firewall bloqueando** - Verifique as configurações de firewall do NAS
4. **Incompatibilidade de versão do SMB** - Alguns NAS ainda exigem compatibilidade com SMB v2/v3; atualize o servidor se ele só expuser configurações antigas de SMB

**Solução:**
- Teste a conexão primeiro pelo PC
- Verifique os logs do NAS em busca de tentativas de conexão
- Tente o endereço IP em vez do nome do host
- Verifique o usuário/senha

---

### ❌ "Tempo esgotado na conexão SFTP"

**Possíveis causas:**
1. Porta errada (padrão: 22)
2. Servidor SSH não está em execução
3. Firewall bloqueando

**Solução:**
```
1. Test with SSH client on PC first:
   ssh username@192.168.1.100
2. Check if SSH service is running
3. Verify port in Settings
```

---

### ❌ "Falha ao entrar no Google Drive"

**Solução:**
1. Limpe os dados do app: Configurações → Apps → FastMediaSorter → Limpar Dados
2. Reinstale o app
3. Verifique as configurações da conta do Google → Segurança → Apps de terceiros

---

### ❌ "Falha ao entrar no OneDrive"

**Solução:**
1. Verifique o status da conta Microsoft
2. Limpe os dados do app: Configurações → Apps → FastMediaSorter → Limpar Dados
3. Verifique as configurações da conta Microsoft → Privacidade → Apps e serviços

---

### ❌ "Falha ao entrar no Dropbox"

**Solução:**
1. Verifique o status da conta Dropbox
2. Limpe os dados do app: Configurações → Apps → FastMediaSorter → Limpar Dados
3. Verifique as configurações da conta Dropbox → Segurança → Apps conectados

---

## Problemas de Desempenho

### ❌ "O app está lento / travando"

**Para pastas grandes (mais de 5000 arquivos):**
1. **Editar pasta** (por recurso) → Ative **"Desativar miniaturas"**
2. Use **filtros** para reduzir os arquivos visíveis
3. Feche outros apps para liberar RAM

**Para pastas de rede:**
1. Verifique a intensidade do sinal Wi-Fi
2. Reduza o tamanho do cache de miniaturas
3. Ative **"Varrer subpastas"** = DESLIGADO se não for necessário

---

### ❌ "Miniaturas não carregam"

**Arquivos locais:**
- Verifique as permissões de armazenamento
- Limpe o cache de miniaturas
- Reinicie o app

**Arquivos de rede:**
- Role mais devagar (as miniaturas carregam sob demanda)
- Verifique a velocidade da rede
- Aumente o tamanho do cache nas Configurações

---

## Erros de Operação de Arquivo

### ❌ "Falha na cópia: Permissão negada"

**Arquivos locais:**
- Conceda permissões de armazenamento: Configurações → Apps → Permissões
- Verifique se a pasta é somente leitura
- Tente mover para outro local

**Arquivos de rede:**
- Verifique se o usuário tem permissão de escrita
- Verifique as configurações de compartilhamento no NAS

---

### ❌ "Não é possível excluir o arquivo"

**Possíveis causas:**
1. O arquivo está aberto em outro app
2. Sem permissão de escrita
3. O arquivo é protegido pelo sistema

**Solução:**
- Feche outros apps
- Verifique as permissões da pasta
- Para rede: confirme que o usuário tem direitos de exclusão

---

### ❌ "Falha na operação de mover"

**Movimentações entre protocolos** (por exemplo, Local → SMB):
- Na verdade, são **cópia + exclusão**
- Requer espaço livre no destino
- Pode demorar mais para arquivos grandes

**Solução:**
- Verifique o espaço disponível
- Use Copiar em vez de Mover por segurança
- Aguarde a operação terminar completamente

---

## Travamentos do App

### ❌ "O app trava ao abrir o player"

**Causas comuns:**
1. Arquivo de vídeo corrompido
2. Codec não suportado
3. Arquivo muito grande (>4GB)

**Solução:**
- Tente reproduzir o arquivo em outro app para verificar
- Verifique o formato do arquivo (suportados: MP4, MKV, MOV)
- Limpe o cache do app

---

### ❌ "Arquivo de mídia não reproduz ou sem som"

**Problema:** O vídeo carrega mas mostra tela preta, ou reproduz sem som.

**Solução:**
1. Toque no botão **ⓘ (Informações)** na barra de ferramentas superior
2. Toque em **"Abrir em Player Externo"**
3. Selecione um player especializado (por exemplo, VLC, MX Player)

Isso usa o recurso *Player Secundário* para repassar codecs não suportados a outros apps.

---

### ❌ "O app trava na inicialização"

**Solução:**
1. Limpe o cache do app: Configurações → Apps → FastMediaSorter → Limpar Cache
2. Se persistir: limpe os dados do app (⚠️ perde as configurações)
3. Reinstale o app como último recurso

---

## Problemas de Interface / Exibição

### ❌ "Zonas de Toque não funcionam"

**Verifique se estão ativadas:**
Configurações → Player → **"Mostrar dica das zonas de toque na primeira execução"** = LIGADO

**Tornar visíveis:**
Configurações → Player → **"Sempre mostrar sobreposição das zonas de toque"** = LIGADO

---

### ❌ "Botões do painel de comandos muito pequenos"

**Solução:**
Configurações → Player → **"Botões compactos do player"** = DESLIGADO

Isso dobra o tamanho de todos os botões e do espaçamento.

---

### ❌ "Tema escuro não funciona"

O app segue o **tema do sistema**:
- Configurações do Android → Tela → Tema escuro = LIGADO

---

## Problemas de Dados

### ❌ "Favoritos desapareceram"

Os favoritos são armazenados **localmente**:
- Limpou os dados do app? → Favoritos perdidos
- Dispositivo novo? → Precisa marcar novamente

**Prevenção:**
- Use **Configurações → Geral → Backups, restauração e exportação de configurações**
- Os favoritos são locais ao dispositivo; se você mudar de telefone, marque-os novamente ou use o fluxo de backup/restauração do app disponível na sua versão

---

### ❌ "A pasta de lixeira continua crescendo"

Os arquivos excluídos vão para a pasta `.trash/` e ficam lá até serem esvaziados manualmente.

**Solução:**
1. Configurações → Gerenciamento → **Exclusão de arquivos e lixeira**
2. Ou exclua manualmente as pastas `.trash/`

---

## Ainda Com Problemas?

### Verifique os Logs
1. Configurações → Gerenciamento → **"Mostrar erros detalhados"** = LIGADO
2. Reproduza o problema
3. Verifique a saída do logcat

### Reportar um Bug
Inclua estas informações:
- Versão do Android
- Modelo do dispositivo
- Passos para reproduzir
- Mensagem de erro (captura de tela)

**Enviar:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

---

---
    
## Problemas de Tradução e EPUB
    
### ❌ "Tradução não funciona ou trava"

**Possíveis causas:**
1. **Modelos ausentes:** O app não conseguiu baixar os modelos de OCR.
2. **Sem Internet:** A primeira execução requer internet para baixar os modelos.
3. **Armazenamento cheio:** Sem espaço para os modelos (~50 MB).

**Solução:**
1. Verifique a conexão com a internet.
2. Vá em **Configurações** → **Mídia** → **Outros**
3. Desative "Ativar Tradução" e ative novamente.
4. Tente mudar o **Idioma de Origem** para "Automático".

---

### ❌ "Livro EPUB não abre"

**Possíveis causas:**
1. **Proteção DRM:** O app só é compatível com EPUBs sem DRM.
2. **Arquivo corrompido:** O arquivo pode estar incompleto.
3. **Arquivo muito grande:** Arquivos com mais de 100MB em rede lenta podem esgotar o tempo.

**Solução:**
1. Verifique se o arquivo abre em outros leitores.
2. Se estiver na rede/nuvem, tente baixá-lo manualmente primeiro.
3. Garanta que a extensão do arquivo seja exatamente `.epub`.

---

## Problemas com Streams de Internet

### O stream não inicia / toca por um segundo e para

**Possíveis causas:**
1. A URL está inativa ou redirecionando para outro protocolo.
2. O servidor exige autenticação (não suportado).
3. http:// sem criptografia bloqueado por uma VPN ou rede corporativa.

**Solução:**
- Toque em **Tentar novamente** na caixa de stream indisponível para tentar de novo.
- Verifique a URL em um navegador.
- Desative a VPN temporariamente para testar.
- Se o stream redirecionar e ainda assim falhar, toque em **Remover** e adicione novamente a URL corrigida.

### O indicador de importação do catálogo não para / trava

O app aplica um tempo limite rápido para downloads de catálogo. Se o indicador travar por mais de ~15 segundos, o host provavelmente está inacessível. Verifique sua conexão com a internet e tente novamente. A caixa se fecha automaticamente ao esgotar o tempo - ela não trava indefinidamente.

### HLS / DASH / RTSP mostra a mensagem 'não suportado'

Em **Standard**, **Legacy** e **XR / noLegal**, os três protocolos são suportados, então essa mensagem aponta para o stream ou seu codec, não para a versão do app. **Lite** e **Photos** não têm a tela Streams, então nenhum stream pode ser adicionado ali para começar.

### A opção Streams não aparece no menu ou nas configurações

- Em **Standard / Legacy / XR / noLegal**: vá em **Configurações > Mídia > Streams** e garanta que **Ativar Streams** esteja ligado. O item na lista suspensa só aparece quando o Streams está ativado.
- Em **Photos**: o recurso Streams não faz parte desta versão.
- Em **Lite**: o recurso Streams também não faz parte desta versão - não há nenhuma opção para ativar nem tela para abrir.

### Os metadados de reprodução atual do ICY não aparecem

Os metadados ICY exigem um stream Icecast/Shoutcast que envie o cabeçalho `Icy-MetaData: 1`. Streams http mp3 simples, sem cabeçalhos ICY, não mostram informações de estação/faixa no mini-controle inferior. Essa é uma limitação do lado do servidor.

---

## Problemas de Conteúdo

### ❌ "Não é possível ver arquivos de Texto ou PDF"

**Solução:**
1. Verifique **Configurações** → **Mídia** → **Documentos**
2. Garanta que **"Suportar Arquivos de Texto"** e **"Suportar Arquivos PDF"** estejam ativados.
3. Verifique os **Filtros** na tela principal (ícone de funil) para garantir que estejam selecionados.
4. **Reescaneie** a pasta (puxar para atualizar).

---

## Limitações Conhecidas

- ⚠️ **Sem suporte a fotos RAW** (CR2, NEF, ARW)
- ⚠️ **Desfazer indisponível na rede** (os arquivos são excluídos permanentemente)
- ⚠️ **O armazenamento na nuvem está presente em todas as versões, exceto Lite**; quais provedores um determinado build oferece ainda pode depender da plataforma do dispositivo, e [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) é a grade por versão
- ⚠️ **Sem sincronização entre dispositivos** (os favoritos são locais)

---

**Última atualização:** 2026-06-05  
**Versão:** Conjunto atual de documentação pública

</div>
