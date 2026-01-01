# ArgusVision — Monitoramento Visual com OpenCV

ArgusVision é o módulo de **monitoramento visual** do ecossistema Argus.  
Ele utiliza **OpenCV** para capturar imagens da câmera, detectar a presença e posição do rosto do aluno e enviar eventos visuais ao servidor.

---

## 🎯 Objetivo

- Monitorar visualmente o aluno durante a prova
- Detectar ausência ou desvio de rosto
- Enviar evidências visuais (frames)
- Complementar os eventos comportamentais do Argus

---

## 🧩 Papel no Ecossistema

O ArgusVision funciona como um **sensor visual distribuído**, operando em paralelo ao Argus.

Ele:
- Não interfere no Eclipse
- Não bloqueia o usuário
- Atua de forma contínua e independente

---

## 🏗️ Arquitetura

### 🔹 ArgusVisionApp
Ponto de entrada da aplicação:
- Carrega OpenCV
- Obtém a sessão ativa do servidor
- Inicializa GUI e monitoramento

---

### 🔹 VisionMonitor
Responsável por:
- Captura de frames da câmera
- Processamento de visão computacional
- Detecção de rosto e posição
- Controle de estabilidade temporal
- Atualização da interface gráfica

---

### 🔹 Detecção de Rosto
- Utiliza **LBP Cascade**
- Classifica o estado do rosto:
  - ROSTO_CENTRO
  - ROSTO_DIREITA / ESQUERDA
  - ROSTO_CIMA / BAIXO
  - SEM_ROSTO
- Eventos são enviados apenas quando:
  - o estado se mantém estável
  - o intervalo mínimo é respeitado

---

### 🔹 Envio de Frames (Diferencial)
- Captura contínua da câmera
- Compressão JPEG
- Codificação Base64
- Envio periódico para o servidor
- Estratégia *latest-frame-wins* (sem acúmulo)

---

### 🔹 VisionEventSender
- Comunicação HTTP com o ArgusServer
- Separação entre:
  - eventos semânticos
  - envio de frames
- Execução assíncrona controlada

---

## 🖥️ Interface Gráfica

- Visualização em tempo real da câmera
- Indicadores de estado:
  - sistema
  - rosto
- Log local de eventos

---

## ⚙️ Requisitos

- Java 11 ou superior
- OpenCV configurado corretamente
- Webcam disponível
- ArgusServer em execução

---

## ▶️ Execução

1. Inicie o ArgusServer
2. Execute o ArgusVision
3. O sistema:
   - Obtém a sessão ativa
   - Inicia a câmera
   - Começa o monitoramento automaticamente

---

## 🔐 Observações

- O ArgusVision não grava vídeo contínuo localmente
- O envio de frames é controlado e eficiente
- Projetado para auditoria e análise posterior

---

## 🔗 Projetos Relacionados

- **[Argus](https://github.com/IsaacLuiz88/Argus)** — Plugin Eclipse (eventos comportamentais)
- **[ArgusServer](https://github.com/IsaacLuiz88/ArgusServer)** — Backend central
