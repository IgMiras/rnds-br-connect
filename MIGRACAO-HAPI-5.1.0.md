# Migração da Camada 4 para o HAPI FHIR 5.1.0

Registro da troca do motor de validação da Camada 4, do HAPI FHIR 7.6.0 para o 5.1.0, a versão
que o Validador Local da RNDS embute. Executada em 11 e 12/11/2026, na branch
`migracao/hapi-5.1.0`, a partir do commit `ba06025`.

## 1. Resumo

1. A Camada 4 roda no HAPI FHIR 5.1.0, com o núcleo `org.hl7.fhir.*` 5.1.0, o mesmo do Validador Local.
2. A suíte inteira passa (202 + 9), no Java 23 e no Java 17. Quebraram 74 testes por um jar ausente (`hapi-fhir-structures-r5`) e 2 por um efeito colateral da 7.6.0; nenhum por veredicto.
3. Das duas divergências com o Validador Local, a do `transient-N` vinha da versão e sumiu. A do `relatesTo` continua e não vem da versão nem da configuração: o aplicativo promove avisos a erro.
4. Os canários continuam reprovando nos dois validadores, e a auditoria de `MustSupport` dá os mesmos números (384, 349, 35).
5. Custo: oito bibliotecas auxiliares atualizadas por vulnerabilidades conhecidas; o núcleo 5.1.0 fica com as dele, sem correção possível dentro da versão.

## 2. Suíte

| | 7.6.0 (`ba06025`) | 5.1.0, 1ª rodada | 5.1.0 (`bb919e6`) | 5.1.0 com a cadeia do aplicativo (E5) |
|---|---|---|---|---|
| Core | 202, todos verdes | 202: 1 falha, 73 erros | 202, todos verdes | 202, todos verdes |
| Catálogo de exames | 9 verdes | 9 verdes | 9 verdes | 9 verdes |
| Falhas por classe | — | A: 74 | — | — |
| Tempo total, Java 23 | 203 s | — | 156 s | 144 s |
| Tempo total, Java 17 | — | — | 148 s | — |
| CI (GitHub Actions, Java 17) | verde | — | pendente: depende do push da branch | — |

Os tempos totais vêm de uma rodada cada, com o Maven e a JVM do ambiente local, e variam de
uma rodada para outra; servem de ordem de grandeza. Os 2 GB por JVM de teste bastam nas duas
versões.

## 3. Testes que mudaram

Nenhum teste mudou de expectativa de veredicto. Mudaram:

| Teste | Causa | Classe | Commit |
|---|---|---|---|
| 74 testes de 15 classes (todos os que validam) | `Could not find the HAPI-FHIR structure JAR on the classpath for version R5`: o `FhirInstanceValidator` da 5.1.0 cria um `FhirContext` R5 e o `hapi-fhir-validation` 5.1.0 não declara o jar. Na 7.6.0 ele vinha por transitividade. | A | `e8006a8` declara a dependência; nenhum teste alterado |
| `FacadeIntegracaoTest.receitaEmbutidaGeraContidos` e `receitaNaRndsEncolheATrilha` | Esperavam o id dos contidos com `#` (`"#prescription"`). Era a validação da 7.6.0 que reescrevia o id em memória ao serializar; o construtor grava sem `#`, como a `RedfmBundleBuilderTest` já conferia. | A | `2b57b4a` |

Previstos na Seção 5 do plano e que **não** mudaram: `RedfmBundleBuilderTest.variacaoReceitaNaRndsComSubstituicao`
e o documento máximo do REDFM na `FacadeIntegracaoTest`. A 5.1.0, com a cadeia da Camada 4,
aprova o `relatesTo` (Seção 4).

## 4. Conferência externa

| Arquivo | 7.6.0 | 5.1.0 | Validador Local |
|---|---|---|---|
| `controle/ria-r--ExemploJSONPreenchidoRIAR.json` (`transient-N`, defeito nº 6) | reprova (3 erros) | **aprova** | aprova |
| `documentos/redfm--variacao-2-substituicao.json` (`relatesTo`, defeito nº 12) | aprova | aprova | reprova (1 erro) |
| Outros 32 documentos | aprovam | aprovam | aprovam |
| 4 canários | reprovam | reprovam | reprovam |

Nenhuma divergência nova. A do `transient-N` vinha da versão do motor. A do `relatesTo` foi
isolada:

- **Não é versão:** com o 5.1.0 nos dois, a mensagem é a mesma, `ValueSet
  http://hl7.org/fhir/R4/valueset-document-relationship-type.html not found by validator`.
- **Não é configuração:** com a cadeia e as opções do aplicativo (branch
  `migracao/hapi-5.1.0-config`, commit `0614236`), a Camada 4 continua emitindo aviso e
  aprovando, e a suíte continua verde.
- **Não é o pacote, na parte medida:** numa sonda com a cadeia do aplicativo e os 401 XML da
  distribuição do REDFM, também aviso e aprovação. Com os 35 JSON do BR-Core, a sonda estourou
  pilha e memória, e essa parte não foi medida.
- **É o tratamento das mensagens no aplicativo:** a `fhir.val.ValidarItemCallable` promove a
  erro, com o prefixo `(PROFILE)`, toda mensagem de qualquer severidade que não seja aviso de
  vínculo `extensible`, informação de vínculo `preferred` ou aviso de referência sem `display`.
  Aplicada às mensagens da Camada 4, essa regra reproduz o veredicto do aplicativo em 33 dos 38
  arquivos. Nos outros cinco (três do SA, dois do RAC), a Camada 4 emite um aviso a mais: o
  `ValueSet` `BRTerminologiaMedicamento`, de vínculo `preferred`, ficou fora do recorte do
  pacote, e a distribuição o traz em `ValueSet/`. A 7.6.0 emitia o mesmo aviso.

Os documentos exportados mudam de bytes, não de conteúdo: o serializador da 5.1.0 põe o
contido `prescription` no fim do array `contained` (quatro documentos do REDFM) e, com o
jackson 2.18, escreve `"chave" : valor`. Com os contidos ordenados, os 37 arquivos têm JSON
idêntico ao da 7.6.0.

## 5. Código alterado

| Commit | Arquivos | Motivo | Tipo |
|---|---|---|---|
| `64d4dc9` | `pom.xml`, `rnds-br-connect-core/pom.xml` | `hapi.version` 5.1.0; sai o `hapi-fhir-caching-caffeine`, que só existe a partir da 6.4.0 | versão |
| `e8006a8` | os mesmos | declara o `hapi-fhir-structures-r5` em escopo `runtime` | API |
| `2b57b4a` | `FacadeIntegracaoTest` | id dos contidos sem `#` | API |
| `b9a3cf1` | `scripts/evidencias-validador-local.py` | `DIVERGENCIAS_CONHECIDAS` sem o controle | evidência |
| `6f2dd35`, `bb919e6` | `evidencias/validador-local/` | conferência externa refeita | evidência |
| `f716fa7` | `pom.xml` | bibliotecas auxiliares sem vulnerabilidade conhecida (Seção 8) | segurança |
| este commit | documentação, Javadoc e descrições de asserção | o que descrevia a 7.6.0 como versão atual | documentação |

O código principal e os testes compilam na 5.1.0 sem nenhuma alteração de API. As classes da
cadeia de validação existem todas, inclusive a `CommonCodeSystemsTerminologyService`.

## 6. Desempenho e memória

Carga do pacote de conformidade do RIA e do REL, que é o mesmo pacote (113 artefatos), com a
JVM recém-iniciada, medida pela `CamadaQuatroTest`. Java 17, cinco rodadas por versão,
alternando as duas, em 12/11/2026:

| | Mediana | Mínimo | Máximo |
|---|---|---|---|
| 7.6.0 | 1251 ms | 1231 ms | 1330 ms |
| 5.1.0 | 1247 ms | 1205 ms | 1324 ms |

A carga é equivalente nas duas versões: o parser XML que lê o pacote é o mesmo tipo de
componente, e a diferença fica dentro da variação entre rodadas. Os 1,24 s e 1,30 s do ADR-03
continuam na mesma faixa. Rodadas isoladas dos outros pacotes, na 5.1.0: RAC 1758 a 2079 ms,
REDFM 1853 a 2284 ms, RIRA 2550 a 3169 ms; na 7.6.0, uma rodada: 1728, 1803 e 2269 ms.

O tempo total da suíte não foi medido com repetição. Rodadas isoladas no Java 23: 203 s na
7.6.0 (a primeira rodada da sessão) e de 156 a 167 s na 5.1.0.

Memória: a suíte passa com os mesmos 2 GB por JVM de teste, nas duas versões e nas duas JVMs.

## 7. O coringa e os snapshots

**O `*`.** Sem a `normalizarVersaoCoringa`, os testes do RIA e do REL têm o mesmo resultado; só
falha a `CamadaQuatroTest.valueSetsUsamVersaoCoringa`, que conta a própria normalização. Numa
sonda, o `vaccineCode` com o código `99999` (ValueSet `BRImunobiologico`, `include` com
`version="*"`) reprova com os mesmos três erros com e sem a normalização, e o original aprova
nos dois. Na 5.1.0 a normalização é desnecessária e inofensiva; fica no código.

**Os snapshots.** A Camada 4 lê os snapshots gravados no REDFM, no SA e no RAC, gerados com a
7.6.0. A `GeradorDeSnapshots` funciona na 5.1.0: os 105 perfis foram regerados numa cópia, sem
falha. Comparação elemento a elemento:

| Pacote | Perfis iguais | Elementos (7.6.0 → 5.1.0) | Atributos diferentes |
|---|---|---|---|
| REDFM | 15 de 15 | 2336 → 2336 | 0 |
| SA | 30 de 43 | 2587 → 2709 | 5 |
| RAC | 33 de 47 | 2843 → 3008 | 43 |

A maior parte é estrutural: a 5.1.0 desdobra os filhos de extensões complexas. As diferenças
de restrição:

- `BRIndividuo`: `Patient.address.line` com `min` 4 na 7.6.0 e 1 na 5.1.0.
- `BRPrescricaoMedicamento`: `dose[x]` e `rate[x]` perdem o perfil `SimpleQuantity`, e o
  `comparator` deixa de ser proibido.
- `BRRegistroAtendimentoClinico`: `Composition.author` deixa de aceitar `BREstabelecimentoSaude-1.0`;
  `title` passa a proibido em três seções; `unidentifiedPatient` passa a `0..*` e perde os vínculos
  `required` de `gender` e `reason`; aparece a fatia `section:informacoesOPM`; o `mustSupport`
  muda em 30 elementos.

Com os snapshots do SA e do RAC regerados pela 5.1.0 (medição temporária, desfeita), a suíte
continua verde, e a auditoria de `MustSupport` passa de 384, 349 e 35 para 408, 373 e 35: o RAC
vai de 103 para 127 elementos `MustSupport`, com 122 emitidos.

## 8. Vulnerabilidades

Consulta ao OSV (`api.osv.dev`) sobre as versões resolvidas, em 12/11/2026.

**Corrigidas** pelo `dependencyManagement` (`f716fa7`): jackson 2.10.0 → 2.18.11, guava
29.0 → 33.7.2, commons-text 1.7 → 1.10.0 (CVE-2022-42889), commons-lang3 3.9 → 3.18.0,
commons-compress 1.20 → 1.26.0, commons-io 2.6 → 2.15.1, httpclient 4.5.12 → 4.5.13 e
commons-codec 1.12 → 1.16.1 (exigido pelo commons-compress). A suíte, a auditoria e a
conferência externa dão o mesmo resultado antes e depois.

**Ficam:**

| Artefato | Por que fica | Exposição no framework |
|---|---|---|
| núcleo `org.hl7.fhir.*` 5.1.0 (XXE: CVE-2024-45294, -51132, -52007; Zip Slip: CVE-2023-24057, -28465; e as de 2026 que a 7.6.0 também tinha) | é o motor que se quer igual ao do aplicativo | o único XML lido é o dos pacotes de conformidade, versionados no repositório; o framework não baixa pacotes, não roda o validador como serviço HTTP e valida documentos montados pelos próprios construtores |
| gson 2.8.5 (CVE-2022-25647) | parser JSON do núcleo 5.1.0 | a falha está na desserialização Java (`writeReplace`) de classes internas do gson, que o framework não faz |
| ucum 1.0.2 (CVE-2024-55887, XXE) | validação de unidades UCUM | o `CommonCodeSystemsTerminologyService` só lê o `/ucum-essence.xml` empacotado no próprio jar do ucum |
| thymeleaf 3.0.9 e ognl 3.1.12 | a correção exige a thymeleaf 3.1, outra linha | o HAPI a usa no gerador de narrativas, que o código principal não chama |

## 9. Pendências do autor

1. **Push, tag e merge.** O push da branch dispara o CI. Antes do merge, a tag `hapi-7.6.0` em
   `ba06025`. A história é linear: merge por fast-forward.
2. **A regra de promoção do aplicativo.** Para a paridade completa, a Camada 4 poderia reprovar
   como o aplicativo, promovendo a erro os avisos fora das três exceções. O REDFM com
   substituição passaria a reprovar (defeito nº 12), e os cinco documentos do SA e do RAC
   também, a menos que o `BRTerminologiaMedicamento` entre no recorte. Hoje a Camada 4 segue o
   critério do HAPI.
3. **Os snapshots do SA e do RAC.** Hoje a Camada 4 é híbrida nesses dois serviços: valida com a
   5.1.0 sobre snapshots da 7.6.0. Regerá-los dá a mesma base do aplicativo, mantém a suíte
   verde e muda os números da auditoria (Seção 7).
4. **A normalização do `*`.** Desnecessária na 5.1.0; removê-la não muda veredicto.
5. **A branch `migracao/hapi-5.1.0-config`.** A cadeia do aplicativo não muda nenhum veredicto
   atual; adotá-la ou descartá-la.
6. **Fora do repositório** (Seção 8.2 do plano): a monografia, os slides e o Notion. Além do
   que o plano lista:
   - o defeito nº 6 deixou de ser acusado pela Camada 4: o texto que diz que só a Camada 4 o
     detecta, ou que ela reprova o exemplo oficial, muda;
   - o argumento das duas versões vira prova de paridade, e a divergência que sobra tem causa
     isolada (Seção 4);
   - a regra de método "a independência é de pacote e de versão do motor", na página "TCC —
     Estado Atual" do Notion;
   - os tempos de carga do ADR-03 (Seção 6).

## 10. Como voltar à 7.6.0

Reverter os commits da migração, do mais novo para o mais antigo:

```bash
git revert --no-edit ba06025..<último commit da migração>
```

Nenhum arquivo de conformidade mudou, e a volta não exige regerar nada. A reversão devolve
também as evidências da 7.6.0 e as duas entradas de `DIVERGENCIAS_CONHECIDAS`, coerentes com o
código revertido. A branch de diagnóstico não entra nessa conta.
