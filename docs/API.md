# CoordinateOffset — Documentação da API

API pública para plugins Paper/Folia que dependem do **CoordinateOffset** (ofuscação de coordenadas X/Z no cliente).

O artefato Maven/Gradle é o módulo **`coordinateoffset-api`** (neste repositório: projeto `:api`).

---

## Dependência

### Gradle (recomendado no seu plugin)

```kotlin
repositories {
    mavenCentral()
    // ou o repositório onde você publica o fork
}

dependencies {
    compileOnly("com.jtprince.coordinateoffset:coordinateoffset-api:6.2.0")
    compileOnly("io.papermc.paper:paper-api:<VERSÃO>-R0.1-SNAPSHOT")
}
```

### Multi-módulo (mesmo repositório)

```kotlin
compileOnly(project(":api"))
```

### `plugin.yml` / `paper-plugin.yml`

```yaml
dependencies:
  server:
    CoordinateOffset:
      load: BEFORE
      required: true   # ou false, se for opcional
```

Se for **opcional**, trate `CoordinateOffset.api()` com `try/catch` (`IllegalStateException` ou `NoClassDefFoundError`).

---

## Acesso à API

```java
import com.jtprince.coordinateoffset.api.CoordinateOffset;
import com.jtprince.coordinateoffset.api.CoordinateOffsetAPI;

CoordinateOffsetAPI api = CoordinateOffset.api();
```

| Classe | Função |
|--------|--------|
| `CoordinateOffset` | Holder do singleton. |
| `CoordinateOffset.api()` | Retorna a instância da API após o CoordinateOffset ter carregado. |
| | Lança `IllegalStateException` se o plugin ainda não inicializou a API. |

Chame `CoordinateOffset.api()` no `onEnable` (ou depois), não antes do CoordinateOffset habilitar.

---

## `CoordinateOffsetAPI` — referência de métodos

### Consulta de offset

#### `FixedOffset getOffset(OffsetPlayer player)`

- Retorna o offset **atual** aplicado ao jogador (componentes X/Z fixos já escalados para o mundo).
- Offsets **subtraem** das coordenadas reais no F3: real `(128, 64, 128)` com offset `(128, 128)` → jogador vê aproximadamente `(0, 64, 0)`.
- Pode mudar em teleporte, troca de mundo, morte/respawn, comandos, etc.
- **Thread:** seguro em qualquer thread.

#### `OffsetData getOffsetData(OffsetPlayer player)`

- Igual a `getOffset`, mas inclui:
  - `offset()` — valor fixo atual;
  - `source()` — origem (`Provider`, bypass, comando, plugin, etc.);
  - `context()` — `OffsetProviderContext` (motivo, localização, jogador).
- **Thread:** seguro em qualquer thread.

---

### Alteração de offset

#### `OffsetChange regenerateOffset(OffsetPlayer player)`

- Gera um **novo** offset usando o provider configurado no `config.yml` do CoordinateOffset (ex.: `RandomOffsetProvider`).
- Se o valor mudar, aplica efeito de “teleporte” no cliente (`forceOffsetSwap`) **sem** mover o jogador no mundo real.
- **Thread:** apenas na thread regional do jogador (Paper/Folia: use `player.getScheduler().run(...)`).
- Retorno: `OffsetChange` — use `offsetChanged()` para saber se houve mudança.

#### `OffsetChange setOffset(OffsetPlayer player, Offset offset)`

- Define offset **direto** (comando/plugin). Também faz swap no cliente se mudou.
- **Desencorajado** para uso contínuo: o offset é perdido no próximo teleporte, mudança de mundo ou relog, mesmo com `regenerateOn*` desligado no config.
- **Preferível:** registrar um `OffsetProvider` customizado + `regenerateOffset`.
- **Thread:** thread regional do jogador.

---

### Ofuscação forçada (staff / vanish / TP)

Extensão do fork MineSky: independe da opção **Randomizar Coordenadas** no `/config` do AnarchyCore.

#### `boolean isObfuscationForced(OffsetPlayer player)`

- `true` se outro plugin ativou ofuscação forçada via API para esse jogador.
- **Thread:** qualquer thread.

#### `OffsetChange setObfuscationForced(OffsetPlayer player, boolean forced)`

| `forced` | Efeito |
|----------|--------|
| `true` | Ofusca coordenadas mesmo com `/config` desligado ou permissão `coordinateoffset.bypass` (exceto lógica interna de prioridade). |
| `false` | Volta à regra normal (`/config`, providers, permissões). |

- Reaplica offset no cliente quando o estado ou o valor muda.
- Estado é limpo no **quit** / disconnect.
- **Thread:** thread regional do jogador.

**Exemplo (vanish):**

```java
CoordinateOffsetAPI coords = CoordinateOffset.api();
OffsetPlayer offsetPlayer = coords.adaptPlayer(staff);

staff.getScheduler().run(yourPlugin, task ->
    coords.setObfuscationForced(offsetPlayer, true), null);

// Ao desativar vanish:
staff.getScheduler().run(yourPlugin, task ->
    coords.setObfuscationForced(offsetPlayer, false), null);
```

---

### Adaptadores de jogador e localização

#### `@Nullable OffsetPlayer getPlayer(UUID playerUuid)`

- Jogador **online**; `null` se não estiver conectado.

#### `OffsetPlayer adaptPlayer(Object platformPlayerObject)`

- Paper: passe `org.bukkit.entity.Player`.
- **Thread:** qualquer thread.
- `ClassCastException` se o tipo for inválido.

#### `OffsetLocation adaptLocation(Object platformLocationObject)`

- Paper: passe `org.bukkit.Location`.
- **Thread:** qualquer thread.

---

### Configuração

#### `CoordinateOffsetConfig getConfig()`

- Config principal (verbose, bypass por permissão, múltiplos de bloco, etc.).
- Disponível após o CoordinateOffset habilitar.
- Providers **não** estão aqui.

#### `CoordinateOffsetProviderConfig getProviderConfig()`

- Providers, default e override rules do `config.yml`.
- **Somente depois** do servidor terminar de carregar (`ServerLoadEvent` / fim do startup).
- Lança `IllegalStateException` se ainda não carregou.

#### `void registerOffsetProviderClass(String className, Function<OffsetProviderConfig, OffsetProvider> deserializeFunction)`

- Registra classe customizada de provider **no `onEnable` do seu plugin**, antes do CoordinateOffset terminar de carregar providers.
- No `config.yml` do CoordinateOffset:

```yaml
offsetProviders:
  meuProvider:
    class: MeuOffsetProvider
    # ... suas chaves
```

- Veja `example-api-plugin` e `ExampleOffsetProvider` no repositório.

---

## Tipos de dados

### `Offset` (interface)

Dois tipos: `FixedOffset` e `ScalableOffset`.

| Método estático / membro | Descrição |
|--------------------------|-----------|
| `Offset.ZERO` | Sem deslocamento. |
| `Offset.fixed(x, z)` | Offset fixo; múltiplos de 16; não escala Nether/End automaticamente. |
| `Offset.scalable(x, z)` | Escala com `coordinateScale` do mundo; alinha a `offsetsAreMultiplesOfBlocks`. |
| `Offset.align(x, z)` | `ScalableOffset` arredondado ao múltiplo configurado. |
| `Offset.random(bound)` | Aleatório alinhado ao múltiplo global. |
| `apply(location)` / `unapply(location)` | Aplica/remove offset em `Location` ou `OffsetLocation`. |
| `negate()` | Inverte sinais. |
| `isZero()` | Ambos componentes zero. |

**Regra:** coordenadas “que o jogador vê” ≈ `offset.apply(localizaçãoReal)`.

### `FixedOffset` (record)

- `int x()`, `int z()` — múltiplos de 16.

### `ScalableOffset`

- Mesma ideia, com escala por mundo antes de virar `FixedOffset` nos pacotes.

### `OffsetChange` (record)

| Campo / método | Descrição |
|----------------|-----------|
| `previousOffsetData()` | Antes da mudança; `null` no join. |
| `newOffsetData()` | Depois da mudança. |
| `offsetChanged()` | `true` se o offset efetivo mudou. |

### `OffsetData` (record)

| Campo | Descrição |
|-------|-----------|
| `offset()` | `FixedOffset` aplicado. |
| `source()` | Origem (ver abaixo). |
| `context()` | Contexto da geração. |

#### `OffsetData.Source`

| Tipo | Significado |
|------|-------------|
| `PermissionBypass` | Zero — permissão `coordinateoffset.bypass`. |
| `BedrockBypass` | Zero — jogador Bedrock (Geyser). |
| `PlayerPreferenceBypass` | Zero — jogador desligou **Randomizar Coordenadas** no `/config` (AnarchyCore). |
| `Provider(provider, overrideRuleIndex)` | Gerado por provider do config. |
| `SetCommand(...)` | Definido por `/offset set`. |
| `PluginSet` | Definido por `setOffset` da API. |

---

## Adaptadores (`com.jtprince.coordinateoffset.adapter`)

### `OffsetPlayer`

| Método | Descrição |
|--------|-----------|
| `getUuid()` / `getName()` | Identificação. |
| `hasPermission(String)` | Permissões Bukkit. |
| `getAllPermissions()` | Conjunto efetivo. |
| `getLocation()` | Localização **real** no servidor. |
| `getPlatformPlayerObject()` | `Player` no Paper. |

### `OffsetLocation` / `OffsetWorld`

Abstração de localização/mundo para providers e core (sem acoplamento direto ao Bukkit na lógica de offset).

---

## `OffsetProvider` — estender comportamento

Classe abstrata para implementar **como** o offset é calculado.

### Obrigatório

| Método | Descrição |
|--------|-----------|
| `provideOffset(OffsetProviderContext context)` | Retorna `Offset` desejado. |
| `serialize()` | Estado para YAML (par com `deserialize` estático). |

### Opcional (callbacks)

| Método | Quando é chamado |
|--------|------------------|
| `onPlayerQuit(OffsetPlayer)` | Saindo do servidor (persistir estado). |
| `onPlayerDisconnect(UUID)` | Conexão fechada (limpar cache). |
| `onOffsetSetByCommand(OffsetSetCommand, OffsetPlayer)` | `/offset set` afetou provider deste jogador. |

Constante: `OffsetProvider.OFFSET_MAX` = 30_000_000.

### `OffsetProviderContext`

| Campo | Descrição |
|-------|-----------|
| `player()` | Destinatário. |
| `previousLocation()` | Antes do evento; `null` no join. |
| `playerLocation()` | Local real após o evento (pode ser antes do teleporte completar). |
| `previousOffset()` | Offset anterior, se houver. |
| `reason()` | `ProvideReason` (veja tabela). |

#### `ProvideReason`

| Valor | Quando |
|-------|--------|
| `JOIN` | Entrada no servidor. |
| `DEATH_RESPAWN` | Respawn após morte. |
| `WORLD_CHANGE` | Mudança de mundo. |
| `TELEPORT` | Teleporte no mesmo mundo. |
| `COMMAND_REGENERATE` | `/offset regenerate`. |
| `COMMAND_SET` | `/offset set` (provider não é chamado para gerar). |
| `PLUGIN_REGENERATE` | `regenerateOffset`, `setObfuscationForced`, etc. |
| `PLUGIN_SET` | `setOffset` da API. |

### `OffsetProviderConfig`

Usado no `deserialize`:

- `getUserDefinedProviderName()` — nome no YAML;
- `getProviderClassName()` — `class:` do YAML;
- `getConfigSection()` — mapa de propriedades.

---

## Prioridade ao gerar offset (Paper / fork MineSky)

Ordem simplificada na criação de um novo offset:

1. **Ofuscação forçada via API** (`setObfuscationForced(true)`) — ignora bypass por permissão e Bedrock para **aplicar** ofuscação (staff em investigação).
2. **Bypass por permissão** `coordinateoffset.bypass` (se config `bypassByPermission` ativo e **sem** forçar API).
3. **Bypass Bedrock** (Geyser).
4. **Preferência `/config`** (AnarchyCore) — se **Randomizar Coordenadas** estiver desligado → offset zero.
5. Regras de **override** no `config.yml`.
6. **Provider padrão** (ex. aleatório).

Integração AnarchyCore **não** faz parte do JAR `:api`; é comportamento do módulo `paper` deste fork. Chaves no menu: `coordinateoffset:coords` / `coordinateoffset:randomize_coords`.

---

## Permissões do plugin (referência)

Nós Bukkit (pacote core, úteis para staff e para entender bypass):

| Nó | Descrição |
|----|-----------|
| `coordinateoffset.*` | Todas as permissões do plugin. |
| `coordinateoffset.bypass` | Sem ofuscação (salvo ofuscação forçada via API). |
| `coordinateoffset.query` | `/offset query` em si. |
| `coordinateoffset.query.others` | Query em outros jogadores. |
| `coordinateoffset.query.verbose` | Detalhes do provider no query. |
| `coordinateoffset.reload` | `/offset reload`. |
| `coordinateoffset.regenerate` | Regenerar próprio offset. |
| `coordinateoffset.regenerate.others` | Regenerar offset de outros. |
| `coordinateoffset.set` | `/offset set` em si. |
| `coordinateoffset.set.others` | Set em outros jogadores. |

---

## Threads (Paper / Folia)

| Operação | Thread |
|----------|--------|
| `getOffset`, `getOffsetData`, `adaptPlayer`, `adaptLocation`, `isObfuscationForced` | Qualquer |
| `regenerateOffset`, `setOffset`, `setObfuscationForced` | Regional do jogador (`player.getScheduler()`) |

Em Folia, **não** use apenas `Bukkit.getGlobalRegionScheduler()` para mutar offset de um jogador específico — use o scheduler **do jogador**.

---

## Fluxo típico de integração

1. `onEnable`: obter `CoordinateOffset.api()` (com try/catch se softdepend).
2. Registrar `OffsetProvider` customizado, se necessário.
3. Em eventos/commands: `adaptPlayer(player)` → ler `getOffset` ou chamar `regenerateOffset` / `setObfuscationForced`.
4. Para vanish/inspeção de bases: `setObfuscationForced(..., true)` ao entrar no modo; `false` ao sair.
5. Para providers/config: só use `getProviderConfig()` após startup completo.

---

## Exemplos no repositório

| Caminho | Conteúdo |
|---------|----------|
| `example-api-plugin/` | Plugin de exemplo (`registerOffsetProviderClass`, `getOffset`, `regenerateOffset`, `setOffset`). |
| `docs/OffsetChangeSequencePaper.md` | Sequência de pacotes ao trocar offset no Paper. |
| `docs/Testing.md` | Testes manuais de regressão. |

---

## Versionamento

A versão do artefato acompanha o projeto raiz (ex.: **6.2.0** em `build.gradle.kts`). Use a mesma versão do JAR **CoordinateOffset-Paper** e do **coordinateoffset-api** no compile do seu plugin.
