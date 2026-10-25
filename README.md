# Orvalho — launcher Liquid Glass para a TV Panasonic (Android TV 9)

Tela inicial própria para a **PANASONIC TV-1131573444** (Android TV 9, build `PTT1.210114.001`, chip Realtek).
A interface é a mesma do protótipo aprovado; o app Android entrega a ela os apps reais, ícones e banners oficiais, entradas da TV e o volume do sistema.

- Pacote: `app.orvalho.launcher` · versão 1.0 (versionCode 1)
- Android mínimo: 9 (API 28) · sem root · sem permissões especiais
- Requer o **Android System WebView 111 ou mais novo** (atualizável pela Play Store). O launcher avisa se estiver desatualizado.

## Estrutura

```
app/src/main/java/app/orvalho/launcher/
  MainActivity.java   tela inicial (WebView), controle remoto, Home/Voltar, servidor interno dos arquivos
  Bridge.java         ponte JS ↔ Android: apps, abrir app, entradas, volume, diagnóstico
  Icons.java          ícones/banners oficiais de cada app e cor de destaque
  VolumeGuard.java    volume máximo e modo noturno (vigia o volume do sistema)
  App.java            inicialização
app/src/main/assets/ui/
  index.html          interface Liquid Glass (gerada a partir do protótipo)
  wallpapers/         papéis de parede
  icons/              ícones de vidro (Configurações, Arquivos, Navegador, Apps)
app/orvalho.jks       chave de assinatura fixa (necessária para atualizar sem desinstalar)
.github/workflows/    compilação automática do APK no GitHub
```

## Compilar

**Opção A — GitHub (não instala nada no PC):** envie este projeto para um repositório seu. A cada envio, a aba *Actions* gera o artefato `orvalho-launcher` com o arquivo `orvalho-launcher.apk`.

**Opção B — no PC (Windows):** instale o Android Studio, abra a pasta do projeto e use *Build → Build APK(s)*, ou no PowerShell, dentro da pasta:

```powershell
.\gradlew.bat assembleRelease
```

O APK sai em `app\build\outputs\apk\release\app-release.apk`.

## Instalar (sem mudar a tela inicial)

Com a Depuração USB ligada na TV e o PC na mesma rede:

```powershell
adb connect 192.168.10.14:5555
adb install -r orvalho-launcher.apk
adb shell am start -n app.orvalho.launcher/.MainActivity
```

O app também aparece na lista de apps da TV como **Orvalho**. Nesta fase, o botão Home ainda abre a tela original.

## Testes na TV

| Teste | Como |
|---|---|
| Controle remoto | Setas, OK, segurar OK (fixar/remover favorito), Voltar, Menu |
| Apps | Abrir 3 apps pela barra e pela página Apps; voltar com Home |
| Entradas | TV ao Vivo → HDMI 1, HDMI 2 e TV Aberta |
| Volume | Ajustes → Volume: +/−, sem som, volume máximo |
| Fluidez | Navegar 30 s e medir (abaixo) |

Medir a fluidez:

```powershell
adb shell dumpsys gfxinfo app.orvalho.launcher reset
# navegue pela interface por ~30 segundos
adb shell dumpsys gfxinfo app.orvalho.launcher
```

Olhe a linha **Janky frames**: abaixo de 10% é fluido. Acima disso, use Ajustes → Modo do vidro → **Leve** e Profundidade → **Desligada** (o launcher também faz isso sozinho, uma única vez, se detectar lentidão).

## Tornar a tela inicial (só com autorização)

**Caminho A (recomendado, reversível):**

```powershell
adb shell cmd package set-home-activity app.orvalho.launcher/.MainActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

A segunda linha deve mostrar `app.orvalho.launcher/.MainActivity`. Aperte Home no controle para confirmar.

**Caminho B (só se o A não funcionar):** desativa a tela original, sem apagar nada.

```powershell
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Se a tela original voltar sozinha depois de reiniciar, o responsável costuma ser o app de configuração inicial:

```powershell
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

## Atualizar

Gere a nova versão (aumente `versionCode` em `app/build.gradle`) e instale por cima. Como a chave é a mesma, favoritos, papel de parede e ajustes são mantidos:

```powershell
adb install -r orvalho-launcher.apk
```

Não apague nem troque o arquivo `app/orvalho.jks`; sem ele, uma nova versão só instala depois de desinstalar a anterior.

## Desfazer tudo

```powershell
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb shell pm enable com.google.android.tvlauncher
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall app.orvalho.launcher
```

Ao desinstalar o Orvalho, a TV volta sozinha para a tela original. Os comandos `pm enable` só são necessários se o Caminho B foi usado; executá-los sem necessidade não causa problema.

**Emergência** (Home não responde): o ADB pela rede continua ativo. Conecte com `adb connect 192.168.10.14:5555` e rode os comandos acima.

## Limitações conhecidas

- **Entrada ativa:** o Android não informa qual entrada está em uso; o cartão mostra a **última entrada aberta pelo launcher**.
- **Continuar assistindo:** mostra os apps de vídeo abertos pelo launcher (data e hora), sem o episódio exato. Ler o progresso de outros apps exige permissão de sistema.
- **Volume inteligente:** salva o volume que você deixou dentro de cada app ao voltar para o launcher. Se o som sair por soundbar via HDMI ARC, quem manda no volume pode ser a soundbar.
- **Volume máximo e modo noturno:** funcionam enquanto o launcher está ativo (o normal para a tela inicial).
- **Controle pelo celular:** existe no protótipo; no app da TV entra na próxima versão.
- **Botão Home:** só abre o Orvalho depois do Caminho A ou B. O app não intercepta o Home por conta própria.
