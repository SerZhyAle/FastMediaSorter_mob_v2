---
layout: default
title: "Coloque o FastMedia no Seu Relógio - FastMediaSorter v2"
permalink: /docs/howto/wear-install-pt.html
---
<div lang="pt" dir="ltr" markdown="1">

# <img src="../icons/doc/ic_watch.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Coloque o FastMedia no Seu Relógio

> **Nível:** Iniciante &bull; **Tempo:** ~5 minutos &bull; **Dispositivo:** Smartwatch Wear OS pareado com um celular Android

> **Duas versões.** A versão do Google Play é um primeiro lançamento pequeno: Calculadora, Cronômetro, Minijogo, Configurações e o tile de Programas. Música, fotos, compartilhamentos de rede e recursos do celular estão apenas na versão completa, um download direto do APK em [Downloads](../DOWNLOADS.md).

{% include lang-switcher.html doc="wear-install" dir="/docs/howto/" current="pt" %}

O FastMedia Wear é a metade para relógio do FastMediaSorter. Uma vez no seu pulso, você pode tocar música e ver fotos direto do relógio, acessar pastas compartilhadas pelo seu celular pareado, e abrir compartilhamentos de rede aos quais o relógio se conecta sozinho. Esta página cuida da instalação e do pareamento.

---

## O Que Você Vai Precisar

- Um smartwatch rodando **Wear OS 3.0** ou mais recente
- Um celular Android com o FastMediaSorter instalado e o relógio já pareado a ele nas configurações do sistema
- Uma conexão Wi-Fi ou de dados móveis no relógio, ou no celular ao qual ele está pareado, para o download

---

## Passo 1 - Instale o FastMedia Wear no Relógio

1. No relógio, abra a lista de apps e toque em **Play Store**.
2. Pesquise por **FastMedia Wear**.
3. Toque em **Instalar** e espere o download terminar. O relógio vai mostrar o app na sua lista de apps quando terminar.

> Os relógios variam em quanto permitem digitar. Se pesquisar no pulso for difícil, abra a Play Store no seu celular, encontre o FastMedia Wear, e escolha seu relógio como alvo de instalação - o relógio baixa sozinho.

### Sem Play Store? Instale um APK pelo ADB

Use esse caminho quando o seu relógio não tem acesso à Play Store. Você precisa de um computador com o Android
SDK Platform-Tools (`adb`) e uma rede Wi-Fi local compartilhada pelo computador e pelo relógio. Não
funciona apenas pela internet.

1. Baixe um APK da página [Direct APK Release](../DOWNLOADS.md):
   - `FastMediaSorter_wear_debug.apk` é a versão de depuração para testes. Ela se instala como
     `com.sza.fastmediasorter.debug`.
   - `FastMediaSorter_wear_release.apk` é a versão assinada e não de depuração. Ela se instala como
     `com.sza.fastmediasorter`.
   - As duas versões têm nomes de pacote diferentes, então podem ficar instaladas lado a lado. Não
     tente instalar um arquivo `.aab` da Play Store com o ADB.
2. No relógio, ative o modo desenvolvedor: **Configurações** → **Sobre o relógio** → toque em **Número da versão** sete
   vezes. Em **Opções do desenvolvedor**, ative a **Depuração ADB** e a **Depuração sem fio**.
3. Em **Depuração sem fio**, escolha **Parear novo dispositivo**. No computador, digite o endereço de pareamento
   e o código mostrados pelo relógio, depois conecte com a porta de conexão separada da tela principal
   de Depuração sem fio:

   ```powershell
   adb pair <watch-ip>:<pairing-port> <six-digit-code>
   adb connect <watch-ip>:<connection-port>
   adb devices
   ```

   Aceite o aviso de depuração no relógio. As portas de pareamento e conexão são diferentes.
4. Instale ou atualize o APK. Use o comando correspondente ao arquivo que você baixou:

   ```powershell
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_debug.apk"
   adb -s <watch-ip>:<connection-port> install -r ".\FastMediaSorter_wear_release.apk"
   ```

   `-r` atualiza o mesmo pacote mantendo seus dados de app. Não converte uma versão de depuração em
   uma versão de lançamento, porque são apps separados.
5. Abra o **FastMedia Wear** na lista de apps do relógio. Se necessário, inicie-o pelo ADB:

   ```powershell
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
   adb -s <watch-ip>:<connection-port> shell am start -n com.sza.fastmediasorter/com.sza.fastmediasorter.wear.MainActivity
   ```

> Este método exige um relógio Wear OS. O Galaxy Watch 3, o Galaxy Watch Active e o Active 2 rodam Tizen
> e não conseguem instalar APKs do Wear OS. Ao terminar, desative a Depuração sem fio, a menos que precise dela
> para outra atualização.

---

## Passo 2 - Ative o Companion do Wear no Celular

O lado do celular fica desligado até você dizer que tem um relógio.

1. Abra o FastMediaSorter no celular.
2. Vá em **Configurações** e abra a aba **Gerenciamento**.
3. Encontre o grupo **Wear OS** e expanda-o.
4. Ative a caixa de seleção **Companion do Wear**.

A caixa de seleção ativa o companion inteiro: o botão que abre a janela dele aparece logo abaixo, uma entrada para ele entra na lista de programas, e ele fica disponível como um tile de painel e um atalho de launcher.

> Versões sem a ponte para o relógio não mostram esse grupo de forma alguma. Se você não conseguir encontrá-lo, você está usando uma edição que não vem com suporte a Wear.

---

## Passo 3 - Escolha o Que Vai para o Relógio

1. No mesmo grupo, toque em **Companion do Wear**. A janela dele se abre sobre o app.
2. Escolha os recursos que você quer que o relógio veja. Nada é enviado até você escolher - uma seleção vazia não envia nada, em vez de enviar toda a sua biblioteca.
3. Ajuste as preferências do próprio relógio aqui também: modo de visualização, comportamento de manter acordado e as seções mostradas na tela inicial do relógio.

---

## Passo 4 - Verifique Que as Duas Partes Se Enxergam

1. Abra o **FastMedia Wear** no relógio.
2. A tela inicial lista suas seções - **Celular**, **Local**, **Recursos**, **Streams** e **Apps**.
3. Toque em **Celular**. As pastas que você selecionou no Passo 3 aparecem.

Se a seção Celular estiver vazia, volte para a janela do companion no celular e confirme que pelo menos um recurso está selecionado.

> **Dica:** Você pode voltar de qualquer tela no seu relógio usando o botão visível de retorno universal na borda esquerda, deslizando a partir da borda esquerda, ou pressionando o botão físico de voltar do seu relógio. Na tela inicial principal, tocar no botão de retorno mostra um ícone de saída (uma seta saindo de uma caixa) para sair do app ou um chevron duplo («) para minimizar a reprodução em segundo plano. Em toda tela que mostra esse botão, um botão de tela preta (um celular com a tela escura) fica de frente para ele na borda direita e apaga a tela do relógio; um toque duplo, um pressionar e segurar, ou o botão físico o traz de volta.

---

## Se Algo Não Funcionar

- **O app do relógio não aparece na Play Store.** Confirme que o relógio roda Wear OS 3.0 ou mais recente. Relógios mais antigos usam um modelo de app diferente e não são suportados.
- **O grupo Wear OS está faltando nas configurações do celular.** A versão que você está usando não traz a ponte para o relógio.
- **A seção Celular no relógio está vazia.** Nada está selecionado na janela do companion, ou o relógio e o celular perderam o pareamento - verifique o pareamento nas configurações do sistema primeiro.
- **A reprodução engasga pela conexão do celular.** O Bluetooth entre o relógio e o celular é limitado. Para audição prolongada, transfira os arquivos para o relógio ou conecte o relógio diretamente a um compartilhamento de rede.

---

## Para Onde Ir Depois

- [Música no Smartwatch](scenario-watch-music-pt.md) - toque sua coleção no relógio, com capa de álbum, modo aleatório e volume pelo bezel.
- [Conectar o Relógio a Compartilhamentos de Rede](scenario-watch-network-pt.md) - acesse um NAS ou um compartilhamento de PC a partir do relógio pelo Wi-Fi, sem o celular.

</div>
