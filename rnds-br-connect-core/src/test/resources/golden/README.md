# Golden files — RIA-R

Oráculo nº 2 da estratégia de validação (Plano de Implementação §1.1): os exemplos oficiais
publicados pelo Ministério da Saúde, corrigidos dos defeitos documentados, usados como
referência **anterior ao código**. O `RiaBundleBuilder` da Fase 1 é escrito até reproduzi-los
campo a campo.

Este documento é material de anexo do TCC. Toda afirmação abaixo é verificável contra os
arquivos citados.

---

## 1. Os dois arquivos e por que são dois

| Arquivo | Origem | Papel |
|---|---|---|
| `ria-r-completo.json` | `ExemploJSONPreenchidoRIAR.json`, distribuído com os arquivos de definição da RNDS | Caminho máximo. Exercita as três extensões, a extensão complexa de pesquisa clínica, o `Condition` por `reasonReference` e todos os campos opcionais. É o alvo de cobertura do Builder. |
| `ria-r-minimo.json` | Derivado dos perfis, não do exemplo | Caminho mínimo. Contém **apenas** o que as cardinalidades exigem. É o alvo de cardinalidade do Builder. |

O exemplo oficial é *kitchen sink*: preenche tudo que o perfil permite. Usá-lo sozinho torna
o critério de saída da Fase 1 ("o Bundle gerado é idêntico ao golden file") ambíguo, porque
não distingue campo obrigatório de campo apenas permitido. O par resolve isso: o mínimo prova
que o Builder não emite lixo; o completo prova que ele não deixa nada de fora.

### Como o mínimo foi derivado

Extração direta do `differential` de `BRRegistroImunobiologicoAdministradoRotina-2.0` e
`BRImunobiologicoAdministrado-3.0`, cruzada com as cardinalidades base do FHIR R4.

Obrigatórios na `Composition`: `status`, `type.coding`, `subject.identifier` (com `system` e
`value`), `date`, `author.identifier`, `title`, `section` (1..1) com `section.entry` (1..1).

Obrigatórios no `Immunization`: `extension` (2..3 — no mínimo `BRGrupoAtendimento` e
`BRCondicaoMaternal`), `status`, `vaccineCode.coding`, `patient.identifier`,
`occurrence[x]`, `site` (1..1), `route` (1..1), `performer` (1..1) com `actor.reference`,
`protocolApplied` (1..1) com `extension:strategy` (1..1) e `doseNumber[x]`.

Ausentes de propósito no mínimo, porque são opcionais no RIA-R: `manufacturer`, `lotNumber`,
`expirationDate`, `reportOrigin`, `primarySource`, `location`, `performer.function`,
`reasonReference` e o `Condition`.

> Nota de contraste com o RIA-C: `Immunization.lotNumber` é `0..1` aqui e `1..1` no perfil de
> Campanha. A obrigatoriedade se inverte entre os dois serviços — é o que a Fase 2 testa.

---

## 2. Correções aplicadas — e a distinção que precisa ser feita

A Arquitetura §8.0.1 lista quatro defeitos nos exemplos oficiais. **Dois deles não estão
presentes nos arquivos JSON distribuídos junto com os arquivos de definição**, e essa
distinção precisa constar do anexo, sob pena de o TCC afirmar um defeito que o arquivo
anexado não exibe.

| # | Defeito (Arquitetura §8.0.1) | Manual impresso | Arquivo JSON distribuído | Correção neste golden file |
|---|---|---|---|---|
| 1 | `meta.profile` do `Immunization` sem hífen (`BRImunobiologicoAdministrado3.0`) | presente | **ausente** — já vem com hífen | nenhuma necessária |
| 2 | `Bundle.identifier.system` = `.../NamingSystem/BR999999` no REL | presente | presente | não se aplica ao RIA-R (Fase 3) |
| 3 | `fullUrl: transient-2` com `id: transient-1` no `Observation` do REL | presente | **ausente** — já vêm coerentes | não se aplica ao RIA-R (Fase 3) |
| 4 | `Condition.code` do REL ora `"01"`, ora `"B04"` | presente | presente (`B04`, mas com `system` de ValueSet) | não se aplica ao RIA-R (Fase 3) |

**Resultado para o RIA-R: `ria-r-completo.json` é cópia literal do arquivo distribuído, sem
uma única alteração.** Isso é um achado, não um não-resultado — significa que o defeito nº 1
é da versão impressa do manual, e que o Portal distribui um artefato mais correto que a sua
própria documentação.

### Conferência terminológica

Todos os 14 códigos do exemplo foram verificados contra os CodeSystems oficiais antes de o
arquivo ser promovido a golden file. Nenhum é inválido:

| Elemento | Código | Display oficial |
|---|---|---|
| `vaccineCode` | `55` | HepAinf |
| `manufacturer.identifier` | `152` | BUTANTAN |
| `site` | `0` | Sem registro no sistema de informação de origem |
| `route` | `0` | Sem registro no sistema de informação de origem |
| `location.identifier` | `10` | BRASIL |
| `reportOrigin` | `01` | Registro anterior/Transcrição de caderneta |
| `performer.function` | `225103` | MÉDICO INFECTOLOGISTA |
| `extension:grupoAtendimento` | `000703` | Povos indígenas vivendo em terras indígenas |
| `extension:condicaoMaternal` | `1` | Não se aplica |
| `protocolApplied.extension:strategy` | `1` | Rotina |
| `doseNumberString` | `1` | 1ª Dose |
| `Condition.code` | `B15` | Hepatite aguda A |
| `Condition.category` | `01` | Principal |
| `Composition.type` | `RIA` | Registro de Imunobiológico Administrado |

---

## 3. Inconsistências semânticas do exemplo oficial

Não são violações de perfil — o validador aprova. São incoerências de conteúdo, e importam
porque um integrador que parta do exemplo as herda.

1. **Estratégia `1` (Rotina) com `reasonReference` presente.** O `Condition` com CID é
   exigido pelo Modelo de Informação apenas na estratégia `2` (Especial). Emiti-lo na Rotina
   é permitido (`0..1`) mas não faz sentido clínico. O Builder não deve replicar isso.
2. **`site` e `route` com código `0`** — literalmente "sem registro no sistema de origem" —
   embora ambos sejam `1..1` no perfil. O exemplo satisfaz a cardinalidade com o código de
   ausência. É a prova de que cardinalidade obrigatória não garante conteúdo informativo, e
   um argumento direto a favor da Frente A da Camada 2.
3. **`reportOrigin` = transcrição de caderneta, com `primarySource: false`, mas com
   `manufacturer` e `lotNumber` preenchidos.** Coerente com o MI RIA-R §7.1, que torna os
   dois *opcionais* — não proibidos — na transcrição. Serve como caso de teste do Elo 8 da
   Frente A.

---

## 4. Avisos esperados na validação

`Immunization.performer.actor.reference` aponta para `Practitioner/012345678912345`, uma
referência **externa ao Bundle** — o profissional não é resource do documento nos três
serviços em escopo. O HAPI emite aviso de referência não resolvida. É esperado e não é erro;
a suíte só reprova mensagens de severidade `ERROR` ou `FATAL`.

---

## 5. Fontes

- `ExemploJSONPreenchidoRIAR.json` — arquivos de definição da adoção FHIR R4, Portal de
  Serviços do DATASUS.
- `BRRegistroImunobiologicoAdministradoRotina-2.0`, `BRImunobiologicoAdministrado-3.0`,
  `BRCID10Avaliado-1.0` — mesma fonte.
- Manual de Integração RIA-R, §7.1 (Modelo de Informação) e §8 (exemplo).
- Arquitetura do Módulo v2.1, §8.0, §8.0.1, §8.0.2 e §8.1.

> ⚠️ **[CITAR FONTE]** — completar com a URL e a data de acesso do download dos arquivos de
> definição, e com a versão/data do PDF do manual RIA-R usado na conferência.
