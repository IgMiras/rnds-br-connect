# Camada 4 × Validador Local da RNDS

Gerado por `scripts/evidencias-validador-local.py` em 12/11/2026 13:22 -0300, sobre o commit `222431f` (árvore limpa). Leia o [README](README.md) antes.

- **Camada 4:** HAPI FHIR 5.1.0, com o pacote do serviço que o framework carrega.
- **Validador Local da RNDS:** HAPI FHIR 5.1.0 (`tarea-fhir-validator.jar`, SHA-256 `6dc1529fddc0c374…`), com a distribuição inteira do serviço.

## Resumo

- Documentos produzidos pelo framework: 32 de 33 com o mesmo veredicto nos dois validadores.
- Canários: 4 de 4 com o mesmo veredicto nos dois validadores.
- Controle: exemplo oficial: 1 de 1 com o mesmo veredicto nos dois validadores.
- Divergências inesperadas: 0.

## Documentos produzidos pelo framework

| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |
|---|---|---|---|---|---|
| `documentos/rac--completo.json` | [aprovado](mensagens/rac--completo.camada4.txt) (0 erros) | [aprovado](mensagens/rac--completo.validador-local.txt) (0 erros) | sim |  | `2243103b7d9cd5d7` |
| `documentos/rac--nao-identificado.json` | [aprovado](mensagens/rac--nao-identificado.camada4.txt) (0 erros) | [aprovado](mensagens/rac--nao-identificado.validador-local.txt) (0 erros) | sim |  | `9400bc423f7b66ae` |
| `documentos/rac--variacao-1-alergia-plano.json` | [aprovado](mensagens/rac--variacao-1-alergia-plano.camada4.txt) (0 erros) | [aprovado](mensagens/rac--variacao-1-alergia-plano.validador-local.txt) (0 erros) | sim |  | `13a3162a5acf34bc` |
| `documentos/rac--variacao-2-cpf-medidas-atestado.json` | [aprovado](mensagens/rac--variacao-2-cpf-medidas-atestado.camada4.txt) (0 erros) | [aprovado](mensagens/rac--variacao-2-cpf-medidas-atestado.validador-local.txt) (0 erros) | sim |  | `2435cc7a08b83bcb` |
| `documentos/rac--variacao-3-contato-prescricao.json` | [aprovado](mensagens/rac--variacao-3-contato-prescricao.camada4.txt) (0 erros) | [aprovado](mensagens/rac--variacao-3-contato-prescricao.validador-local.txt) (0 erros) | sim |  | `87546f0b06685975` |
| `documentos/redfm--receita-embutida.json` | [aprovado](mensagens/redfm--receita-embutida.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--receita-embutida.validador-local.txt) (0 erros) | sim |  | `473136acf48759bf` |
| `documentos/redfm--receita-na-rnds.json` | [aprovado](mensagens/redfm--receita-na-rnds.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--receita-na-rnds.validador-local.txt) (0 erros) | sim |  | `b43d6578cb10befe` |
| `documentos/redfm--variacao-1-sem-textos-livres.json` | [aprovado](mensagens/redfm--variacao-1-sem-textos-livres.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--variacao-1-sem-textos-livres.validador-local.txt) (0 erros) | sim |  | `de04f11f0af93ca2` |
| `documentos/redfm--variacao-2-substituicao.json` | [aprovado](mensagens/redfm--variacao-2-substituicao.camada4.txt) (0 erros) | [reprovado](mensagens/redfm--variacao-2-substituicao.validador-local.txt) (1 erro) | **não** | Divergência conhecida. O vínculo required de Composition.relatesTo.code, herdado do br-core-composition, aponta para uma página de documentação (defeito nº 12). Com o mesmo motor, o HAPI FHIR 5.1.0, e a mesma mensagem, a Camada 4 registra o ValueSet não encontrado como aviso, e o Validador Local, como erro: a diferença vem da configuração ou do pacote, e não da versão. | `585f7fe67f55b278` |
| `documentos/redfm--variacao-3-dispensacao-parcial.json` | [aprovado](mensagens/redfm--variacao-3-dispensacao-parcial.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--variacao-3-dispensacao-parcial.validador-local.txt) (0 erros) | sim |  | `acec02e330a58ec7` |
| `documentos/rel--completo.json` | [aprovado](mensagens/rel--completo.camada4.txt) (0 erros) | [aprovado](mensagens/rel--completo.validador-local.txt) (0 erros) | sim |  | `66e914269486a7a5` |
| `documentos/rel--variacao-1-sem-patogeno-cpf.json` | [aprovado](mensagens/rel--variacao-1-sem-patogeno-cpf.camada4.txt) (0 erros) | [aprovado](mensagens/rel--variacao-1-sem-patogeno-cpf.validador-local.txt) (0 erros) | sim |  | `f293cc9eb9e1d1e1` |
| `documentos/rel--variacao-2-quantitativo-interpretacao.json` | [aprovado](mensagens/rel--variacao-2-quantitativo-interpretacao.camada4.txt) (0 erros) | [aprovado](mensagens/rel--variacao-2-quantitativo-interpretacao.validador-local.txt) (0 erros) | sim |  | `280c2574d2a6c48d` |
| `documentos/rel--variacao-3-tres-exames.json` | [aprovado](mensagens/rel--variacao-3-tres-exames.camada4.txt) (0 erros) | [aprovado](mensagens/rel--variacao-3-tres-exames.validador-local.txt) (0 erros) | sim |  | `10842a5a08df31a3` |
| `documentos/ria-c--completo.json` | [aprovado](mensagens/ria-c--completo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--completo.validador-local.txt) (0 erros) | sim |  | `20a4c407a136bae4` |
| `documentos/ria-c--variacao-1-cns.json` | [aprovado](mensagens/ria-c--variacao-1-cns.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--variacao-1-cns.validador-local.txt) (0 erros) | sim |  | `7768039310d03dac` |
| `documentos/ria-c--variacao-2-outros-codigos.json` | [aprovado](mensagens/ria-c--variacao-2-outros-codigos.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--variacao-2-outros-codigos.validador-local.txt) (0 erros) | sim |  | `51da8ffa54fd7499` |
| `documentos/ria-c--variacao-3-lote-alfanumerico.json` | [aprovado](mensagens/ria-c--variacao-3-lote-alfanumerico.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--variacao-3-lote-alfanumerico.validador-local.txt) (0 erros) | sim |  | `6bc577b39fbf8a3c` |
| `documentos/ria-r--completo.json` | [aprovado](mensagens/ria-r--completo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--completo.validador-local.txt) (0 erros) | sim |  | `4b145880cde1e447` |
| `documentos/ria-r--minimo.json` | [aprovado](mensagens/ria-r--minimo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--minimo.validador-local.txt) (0 erros) | sim |  | `2eea0c781c541eab` |
| `documentos/ria-r--variacao-1-especial-cid-cbo.json` | [aprovado](mensagens/ria-r--variacao-1-especial-cid-cbo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--variacao-1-especial-cid-cbo.validador-local.txt) (0 erros) | sim |  | `60e4f3baf000a6b1` |
| `documentos/ria-r--variacao-2-transcricao.json` | [aprovado](mensagens/ria-r--variacao-2-transcricao.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--variacao-2-transcricao.validador-local.txt) (0 erros) | sim |  | `77327689f125cb72` |
| `documentos/ria-r--variacao-3-lote-pesquisa-parcial.json` | [aprovado](mensagens/ria-r--variacao-3-lote-pesquisa-parcial.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--variacao-3-lote-pesquisa-parcial.validador-local.txt) (0 erros) | sim |  | `6fc80c9143ebe824` |
| `documentos/rira--tres-eventos.json` | [aprovado](mensagens/rira--tres-eventos.camada4.txt) (0 erros) | [aprovado](mensagens/rira--tres-eventos.validador-local.txt) (0 erros) | sim |  | `00a99383167e821f` |
| `documentos/rira--um-evento.json` | [aprovado](mensagens/rira--um-evento.camada4.txt) (0 erros) | [aprovado](mensagens/rira--um-evento.validador-local.txt) (0 erros) | sim |  | `4e165d5c9bcf3c0b` |
| `documentos/rira--variacao-1-sete-estados.json` | [aprovado](mensagens/rira--variacao-1-sete-estados.camada4.txt) (0 erros) | [aprovado](mensagens/rira--variacao-1-sete-estados.validador-local.txt) (0 erros) | sim |  | `c32b8d361c94bd01` |
| `documentos/rira--variacao-2-urgente-sem-executante.json` | [aprovado](mensagens/rira--variacao-2-urgente-sem-executante.camada4.txt) (0 erros) | [aprovado](mensagens/rira--variacao-2-urgente-sem-executante.validador-local.txt) (0 erros) | sim |  | `c24f56046e4bcedb` |
| `documentos/rira--variacao-3-sem-agendamento.json` | [aprovado](mensagens/rira--variacao-3-sem-agendamento.camada4.txt) (0 erros) | [aprovado](mensagens/rira--variacao-3-sem-agendamento.validador-local.txt) (0 erros) | sim |  | `3d171c1acf080826` |
| `documentos/sa--completo.json` | [aprovado](mensagens/sa--completo.camada4.txt) (0 erros) | [aprovado](mensagens/sa--completo.validador-local.txt) (0 erros) | sim |  | `905e589b7a6e4da2` |
| `documentos/sa--sexta-demonstracao.json` | [aprovado](mensagens/sa--sexta-demonstracao.camada4.txt) (0 erros) | [aprovado](mensagens/sa--sexta-demonstracao.validador-local.txt) (0 erros) | sim |  | `f4f535b2989b9c1e` |
| `documentos/sa--variacao-1-sem-alergia-prescricao.json` | [aprovado](mensagens/sa--variacao-1-sem-alergia-prescricao.camada4.txt) (0 erros) | [aprovado](mensagens/sa--variacao-1-sem-alergia-prescricao.validador-local.txt) (0 erros) | sim |  | `4d7e124c3fad9dfb` |
| `documentos/sa--variacao-2-alergia-sem-prescricao.json` | [aprovado](mensagens/sa--variacao-2-alergia-sem-prescricao.camada4.txt) (0 erros) | [aprovado](mensagens/sa--variacao-2-alergia-sem-prescricao.validador-local.txt) (0 erros) | sim |  | `b2f2a6cba0a652a5` |
| `documentos/sa--variacao-3-cpf-sem-diagnostico.json` | [aprovado](mensagens/sa--variacao-3-cpf-sem-diagnostico.camada4.txt) (0 erros) | [aprovado](mensagens/sa--variacao-3-cpf-sem-diagnostico.validador-local.txt) (0 erros) | sim |  | `54d223d206ed4a86` |

## Canários

| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |
|---|---|---|---|---|---|
| `canarios/rac--canario-cmd-1.json` | [reprovado](mensagens/rac--canario-cmd-1.camada4.txt) (4 erros) | [reprovado](mensagens/rac--canario-cmd-1.validador-local.txt) (8 erros) | sim | Canário: `rac--completo` com `Procedure.identifier` fora da máscara da invariante cmd-1; precisa reprovar nos dois. | `8d7032ffbf5c7a14` |
| `canarios/redfm--canario-dfm-2.json` | [reprovado](mensagens/redfm--canario-dfm-2.camada4.txt) (2 erros) | [reprovado](mensagens/redfm--canario-dfm-2.validador-local.txt) (3 erros) | sim | Canário: `redfm--receita-embutida` sem `MedicationDispense.quantity`, que a invariante dfm-2 exige; precisa reprovar nos dois. | `26673c1244f4a4a7` |
| `canarios/ria-r--canario-sem-route.json` | [reprovado](mensagens/ria-r--canario-sem-route.camada4.txt) (2 erros) | [reprovado](mensagens/ria-r--canario-sem-route.validador-local.txt) (3 erros) | sim | Canário: `ria-r--minimo` sem `Immunization.route`, 1..1 no perfil; precisa reprovar nos dois. | `87b371c55f8573bc` |
| `canarios/rira--canario-sem-event.json` | [reprovado](mensagens/rira--canario-sem-event.camada4.txt) (1 erro) | [reprovado](mensagens/rira--canario-sem-event.validador-local.txt) (1 erro) | sim | Canário: `rira--um-evento` sem `Composition.event`, 1..* no perfil; precisa reprovar nos dois. | `17bff81c4eb84909` |

## Controle: exemplo oficial

| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |
|---|---|---|---|---|---|
| `controle/ria-r--ExemploJSONPreenchidoRIAR.json` | [aprovado](mensagens/ria-r--ExemploJSONPreenchidoRIAR.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--ExemploJSONPreenchidoRIAR.validador-local.txt) (0 erros) | sim |  | `0ef5cec4f965ec10` |

## Pacotes

| Sigla | Camada 4 (classpath do core) | Validador Local (rnds-artefatos) |
|---|---|---|
| `ria-r` | `conformance` | `br-core-ril-ria-rel` |
| `ria-c` | `conformance` | `br-core-ril-ria-rel` |
| `rel` | `conformance` | `br-core-ril-ria-rel` |
| `rira` | `conformance/rira` | `Artefatos_Validador_RIRA` |
| `redfm` | `conformance/redfm` | `modelagem-redfm` |
| `sa` | `conformance/sa` | `arquivos_definicoes_SA` |
| `rac` | `conformance/rac` | `arquivos_definicoes_SA` |
