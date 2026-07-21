# 📷 ArgusVision — o Olho na Webcam

![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk&logoColor=white)
![OpenCV](https://img.shields.io/badge/OpenCV-4.12-5C3EE8?logo=opencv&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)
![TCC](https://img.shields.io/badge/projeto-TCC-blueviolet)

> O [Argus](../Argus) vê o que acontece na tela. O ArgusVision vê o que acontece **na frente da tela**.

**ArgusVision** é o módulo de monitoramento visual do ecossistema Argus: usa **OpenCV** pra ligar a webcam do aluno, detectar a posição do rosto em tempo real e mandar frames pro [ArgusServer](../ArgusServer), que os repassa ao vivo pro dashboard do professor.

Ele é sempre a **última peça a entrar em cena** — só faz sentido existir depois que o servidor está de pé e o aluno já fez login no plugin.

---

## 🎯 O que ele faz

- Descobre sozinho quem é o aluno (sem perguntar nada na tela — ver seção [Identificação do aluno](#-identificação-do-aluno)).
- Liga a webcam e roda um classificador **LBP Cascade** pra achar o rosto.
- Classifica a posição do rosto: `ROSTO_CENTRO`, `ROSTO_ESQUERDA`, `ROSTO_DIREITA`, `ROSTO_CIMA`, `ROSTO_BAIXO` ou `SEM_ROSTO`.
- Só manda um evento de posição quando o estado **se estabiliza** por um tempo mínimo — pra não spammar o servidor a cada leve tremida de câmera.
- Manda um frame (JPEG comprimido, Base64) por segundo, sempre o **mais recente** — sem fila, sem acúmulo (`latest-frame-wins`).
- Fica de olho na própria sessão: a cada 3 segundos confere no servidor se ela ainda está ativa, e se encerra sozinho quando a prova termina.
- Garante que só existe **uma instância rodando por máquina** — afinal, só existe uma webcam física por aluno.

---

## 🙋 Identificação do aluno

Nunca há uma janela perguntando "qual é o seu nome?" no ArgusVision — essa pergunta já foi feita uma vez, no login do plugin. A partir daí, o ArgusVision descobre quem é o aluno em duas etapas, nessa ordem:

1. **Argumento de linha de comando** — é assim que o [Argus](../Argus) já lança o processo automaticamente (`ArgusVisionLauncher`), passando o nome do aluno direto.
2. **Sessão local gravada pelo plugin** — se for executado manualmente (sem argumento), ele lê `~/ArgusLogs/current_session.properties`, um arquivo que o plugin grava assim que confirma o login com o servidor. Ou seja: mesmo rodando o `.jar` na mão, ele "sabe" quem logou por último nessa máquina.

Se nenhuma das duas fontes tiver um nome, ele encerra com uma mensagem de erro clara — nunca trava esperando input.

---

## 🧩 Papel no ecossistema

```
  Argus (plugin)                 ArgusVision                    ArgusServer
       │                              │                               │
       │ login confirmado             │                               │
       ├──────────────────────────────►                               │
       │ (lança o processo)           │                               │
       │                              │──── GET sessão do aluno ─────►│
       │                              │◄──── student/exam/session ────┤
       │                              │                               │
       │                              │── POST frames + vision ──────►│
       │                              │        (a cada 1s / evento)   │
       │                              │                               │
       │                              │── GET confere sessão (3/3s) ─►│
```

---

## 🏗️ Estrutura interna

| Classe | Responsabilidade |
|---|---|
| `ArgusVisionApp` | Ponto de entrada: carrega OpenCV, resolve a identidade do aluno, garante instância única e inicia o monitoramento. |
| `SingleInstanceGuard` | Trava (via `FileLock` do sistema operacional) que impede duas instâncias do ArgusVision na mesma máquina disputando a mesma webcam. |
| `LocalSessionReader` | Lê a sessão que o plugin gravou localmente, quando não há argumento de linha de comando. |
| `VisionMonitor` | O coração do processo: captura frames, roda a detecção de rosto, controla a estabilidade temporal dos eventos e aciona o envio periódico. |
| `FrameEncoder` | Converte o frame (`Mat` do OpenCV) em JPEG comprimido e depois Base64. |
| `VisionEventSender` | Manda eventos simples e frames pro ArgusServer via HTTP, com filas assíncronas separadas para cada tipo. |
| `SessionClient` | Busca a sessão ativa do aluno no servidor (`fetchByStudent`) e detecta quando ela mudou ou terminou. |
| `HeadlessVisionOutput` | Implementação "sem janela" da saída de vídeo — a usada por padrão, já que o ArgusVision roda em segundo plano. |
| `FileLogger` | Grava logs locais em `~/ArgusLogsVision/`, nomeados por aluno + prova + data. |

> Existe também uma `CameraViewer` (janela Swing com preview ao vivo, indicadores e log) pronta no código, caso um dia se queira rodar o ArgusVision de forma visível — hoje ele roda sempre em modo headless (`HeadlessVisionOutput`).

---

## ⚙️ Configuração

`config.properties`, na raiz do projeto:

```properties
camera.index=0
```

> No momento, o endereço do ArgusServer está fixo em `http://localhost:8080` diretamente no código (`SessionClient`, `VisionEventSender`) — se o servidor rodar em outra máquina, ajuste essas constantes antes de gerar o `.jar`. O `camera.index` é a única configuração realmente lida em runtime (útil se a máquina tiver mais de uma câmera).

---

## ▶️ Como rodar

### Requisitos
- Java 17+ (a mesma versão usada no restante do ecossistema)
- OpenCV com os bindings Java configurados (jar + biblioteca nativa da plataforma)
- Webcam disponível
- [ArgusServer](../ArgusServer) rodando, com uma sessão de aluno já registrada

### Build
```bash
mvn package
```

### Execução

**Modo normal (recomendado):** não faça nada — o [Argus](../Argus) já lança o ArgusVision sozinho, assim que o login do aluno é confirmado (se `argusvision.enabled=true` estiver configurado no plugin).

**Modo manual (para testes):**
```bash
java -Djava.library.path=<pasta-com-as-libs-nativas-do-opencv> -jar ArgusVision.jar "Nome do Aluno"
```
Ou, se o plugin já fez login nessa máquina, sem argumento nenhum:
```bash
java -Djava.library.path=<pasta-com-as-libs-nativas-do-opencv> -jar ArgusVision.jar
```

---

## 🔐 Observações

- O ArgusVision **não grava vídeo contínuo** localmente — só processa e descarta frame a frame.
- O envio de frames é deliberadamente "com perda": se um novo frame chega antes do anterior ser enviado, o antigo é descartado. Isso evita fila crescendo e atraso acumulado.
- Uma webcam, uma instância — a trava de instância única existe justamente porque isso já causou um aluno "roubar" o feed de outro em testes com múltiplos logins na mesma máquina.

---

## 🔗 Projetos relacionados

- **[Argus](https://github.com/IsaacLuiz88/Argus)** — plugin Eclipse, quem inicia o ArgusVision.
- **[ArgusServer](https://github.com/IsaacLuiz88/ArgusServer)** — backend central, dono da sessão e do dashboard.
