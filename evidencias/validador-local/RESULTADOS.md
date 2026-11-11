# Camada 4 × Validador Local da RNDS

Gerado por `scripts/evidencias-validador-local.py` em 11/11/2026 17:09 -0300, sobre o commit `cc65a2d` (árvore limpa). Leia o [README](README.md) antes.

- **Camada 4:** HAPI FHIR 7.6.0, com o pacote do serviço que o framework carrega.
- **Validador Local da RNDS:** HAPI FHIR 5.1.0 (`tarea-fhir-validator.jar`, SHA-256 `6dc1529fddc0c374…`), com a distribuição inteira do serviço.

## Resumo

- Documentos produzidos pelo framework: 32 de 33 com o mesmo veredicto nos dois validadores.
- Canários: 4 de 4 com o mesmo veredicto nos dois validadores.
- Controle: exemplo oficial: 0 de 1 com o mesmo veredicto nos dois validadores.
- Divergências inesperadas: 0.

## Documentos produzidos pelo framework

| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |
|---|---|---|---|---|---|
| `documentos/rac--completo.json` | [aprovado](mensagens/rac--completo.camada4.txt) (0 erros) | [aprovado](mensagens/rac--completo.validador-local.txt) (0 erros) | sim |  | `c25f3f5f22450480` |
| `documentos/rac--nao-identificado.json` | [aprovado](mensagens/rac--nao-identificado.camada4.txt) (0 erros) | [aprovado](mensagens/rac--nao-identificado.validador-local.txt) (0 erros) | sim |  | `cee243ae4b3931fe` |
| `documentos/rac--variacao-1-alergia-plano.json` | [aprovado](mensagens/rac--variacao-1-alergia-plano.camada4.txt) (0 erros) | [aprovado](mensagens/rac--variacao-1-alergia-plano.validador-local.txt) (0 erros) | sim |  | `4ad2880ca27b438b` |
| `documentos/rac--variacao-2-cpf-medidas-atestado.json` | [aprovado](mensagens/rac--variacao-2-cpf-medidas-atestado.camada4.txt) (0 erros) | [aprovado](mensagens/rac--variacao-2-cpf-medidas-atestado.validador-local.txt) (0 erros) | sim |  | `9c95146e823e67a5` |
| `documentos/rac--variacao-3-contato-prescricao.json` | [aprovado](mensagens/rac--variacao-3-contato-prescricao.camada4.txt) (0 erros) | [aprovado](mensagens/rac--variacao-3-contato-prescricao.validador-local.txt) (0 erros) | sim |  | `f7769918af576bec` |
| `documentos/redfm--receita-embutida.json` | [aprovado](mensagens/redfm--receita-embutida.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--receita-embutida.validador-local.txt) (0 erros) | sim |  | `5b21ab29e47ee8b3` |
| `documentos/redfm--receita-na-rnds.json` | [aprovado](mensagens/redfm--receita-na-rnds.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--receita-na-rnds.validador-local.txt) (0 erros) | sim |  | `afa632af95036e56` |
| `documentos/redfm--variacao-1-sem-textos-livres.json` | [aprovado](mensagens/redfm--variacao-1-sem-textos-livres.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--variacao-1-sem-textos-livres.validador-local.txt) (0 erros) | sim |  | `17ced056b9bbd3c4` |
| `documentos/redfm--variacao-2-substituicao.json` | [aprovado](mensagens/redfm--variacao-2-substituicao.camada4.txt) (0 erros) | [reprovado](mensagens/redfm--variacao-2-substituicao.validador-local.txt) (1 erro) | **não** | Divergência conhecida. O vínculo required de Composition.relatesTo.code, herdado do br-core-composition, aponta para uma página de documentação (defeito nº 12). A Camada 4 registra o ValueSet não encontrado como aviso; o Validador Local, como erro. | `56f98d693aec2ad3` |
| `documentos/redfm--variacao-3-dispensacao-parcial.json` | [aprovado](mensagens/redfm--variacao-3-dispensacao-parcial.camada4.txt) (0 erros) | [aprovado](mensagens/redfm--variacao-3-dispensacao-parcial.validador-local.txt) (0 erros) | sim |  | `f5b77a4e673b7f2c` |
| `documentos/rel--completo.json` | [aprovado](mensagens/rel--completo.camada4.txt) (0 erros) | [aprovado](mensagens/rel--completo.validador-local.txt) (0 erros) | sim |  | `2064ac38ebb21dd1` |
| `documentos/rel--variacao-1-sem-patogeno-cpf.json` | [aprovado](mensagens/rel--variacao-1-sem-patogeno-cpf.camada4.txt) (0 erros) | [aprovado](mensagens/rel--variacao-1-sem-patogeno-cpf.validador-local.txt) (0 erros) | sim |  | `5dd8d8bed1914013` |
| `documentos/rel--variacao-2-quantitativo-interpretacao.json` | [aprovado](mensagens/rel--variacao-2-quantitativo-interpretacao.camada4.txt) (0 erros) | [aprovado](mensagens/rel--variacao-2-quantitativo-interpretacao.validador-local.txt) (0 erros) | sim |  | `08c9968b564207a7` |
| `documentos/rel--variacao-3-tres-exames.json` | [aprovado](mensagens/rel--variacao-3-tres-exames.camada4.txt) (0 erros) | [aprovado](mensagens/rel--variacao-3-tres-exames.validador-local.txt) (0 erros) | sim |  | `173444e7dd33b6e2` |
| `documentos/ria-c--completo.json` | [aprovado](mensagens/ria-c--completo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--completo.validador-local.txt) (0 erros) | sim |  | `7b06aefa3b07c179` |
| `documentos/ria-c--variacao-1-cns.json` | [aprovado](mensagens/ria-c--variacao-1-cns.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--variacao-1-cns.validador-local.txt) (0 erros) | sim |  | `dab59106e2c448d3` |
| `documentos/ria-c--variacao-2-outros-codigos.json` | [aprovado](mensagens/ria-c--variacao-2-outros-codigos.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--variacao-2-outros-codigos.validador-local.txt) (0 erros) | sim |  | `5ee8049f541e979d` |
| `documentos/ria-c--variacao-3-lote-alfanumerico.json` | [aprovado](mensagens/ria-c--variacao-3-lote-alfanumerico.camada4.txt) (0 erros) | [aprovado](mensagens/ria-c--variacao-3-lote-alfanumerico.validador-local.txt) (0 erros) | sim |  | `780f657b6abdd1a9` |
| `documentos/ria-r--completo.json` | [aprovado](mensagens/ria-r--completo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--completo.validador-local.txt) (0 erros) | sim |  | `aed8c7ee3c56cc89` |
| `documentos/ria-r--minimo.json` | [aprovado](mensagens/ria-r--minimo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--minimo.validador-local.txt) (0 erros) | sim |  | `ab8253de1e4d166c` |
| `documentos/ria-r--variacao-1-especial-cid-cbo.json` | [aprovado](mensagens/ria-r--variacao-1-especial-cid-cbo.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--variacao-1-especial-cid-cbo.validador-local.txt) (0 erros) | sim |  | `1ec0b36a81e0c2db` |
| `documentos/ria-r--variacao-2-transcricao.json` | [aprovado](mensagens/ria-r--variacao-2-transcricao.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--variacao-2-transcricao.validador-local.txt) (0 erros) | sim |  | `ef6e2c107a8e78f1` |
| `documentos/ria-r--variacao-3-lote-pesquisa-parcial.json` | [aprovado](mensagens/ria-r--variacao-3-lote-pesquisa-parcial.camada4.txt) (0 erros) | [aprovado](mensagens/ria-r--variacao-3-lote-pesquisa-parcial.validador-local.txt) (0 erros) | sim |  | `b12af7a8e4f376b9` |
| `documentos/rira--tres-eventos.json` | [aprovado](mensagens/rira--tres-eventos.camada4.txt) (0 erros) | [aprovado](mensagens/rira--tres-eventos.validador-local.txt) (0 erros) | sim |  | `76dd7310a8053437` |
| `documentos/rira--um-evento.json` | [aprovado](mensagens/rira--um-evento.camada4.txt) (0 erros) | [aprovado](mensagens/rira--um-evento.validador-local.txt) (0 erros) | sim |  | `5990f61a639b50bd` |
| `documentos/rira--variacao-1-sete-estados.json` | [aprovado](mensagens/rira--variacao-1-sete-estados.camada4.txt) (0 erros) | [aprovado](mensagens/rira--variacao-1-sete-estados.validador-local.txt) (0 erros) | sim |  | `df3eacb49ded62f3` |
| `documentos/rira--variacao-2-urgente-sem-executante.json` | [aprovado](mensagens/rira--variacao-2-urgente-sem-executante.camada4.txt) (0 erros) | [aprovado](mensagens/rira--variacao-2-urgente-sem-executante.validador-local.txt) (0 erros) | sim |  | `04dd76de4ffbed9a` |
| `documentos/rira--variacao-3-sem-agendamento.json` | [aprovado](mensagens/rira--variacao-3-sem-agendamento.camada4.txt) (0 erros) | [aprovado](mensagens/rira--variacao-3-sem-agendamento.validador-local.txt) (0 erros) | sim |  | `81e1ad99a4f3d61b` |
| `documentos/sa--completo.json` | [aprovado](mensagens/sa--completo.camada4.txt) (0 erros) | [aprovado](mensagens/sa--completo.validador-local.txt) (0 erros) | sim |  | `32e940fcc0c03ae4` |
| `documentos/sa--sexta-demonstracao.json` | [aprovado](mensagens/sa--sexta-demonstracao.camada4.txt) (0 erros) | [aprovado](mensagens/sa--sexta-demonstracao.validador-local.txt) (0 erros) | sim |  | `32b8ce07e90fdc2c` |
| `documentos/sa--variacao-1-sem-alergia-prescricao.json` | [aprovado](mensagens/sa--variacao-1-sem-alergia-prescricao.camada4.txt) (0 erros) | [aprovado](mensagens/sa--variacao-1-sem-alergia-prescricao.validador-local.txt) (0 erros) | sim |  | `6a88518f655c542c` |
| `documentos/sa--variacao-2-alergia-sem-prescricao.json` | [aprovado](mensagens/sa--variacao-2-alergia-sem-prescricao.camada4.txt) (0 erros) | [aprovado](mensagens/sa--variacao-2-alergia-sem-prescricao.validador-local.txt) (0 erros) | sim |  | `ec0193455289b431` |
| `documentos/sa--variacao-3-cpf-sem-diagnostico.json` | [aprovado](mensagens/sa--variacao-3-cpf-sem-diagnostico.camada4.txt) (0 erros) | [aprovado](mensagens/sa--variacao-3-cpf-sem-diagnostico.validador-local.txt) (0 erros) | sim |  | `628b0b1348c41307` |

## Canários

| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |
|---|---|---|---|---|---|
| `canarios/rac--canario-cmd-1.json` | [reprovado](mensagens/rac--canario-cmd-1.camada4.txt) (4 erros) | [reprovado](mensagens/rac--canario-cmd-1.validador-local.txt) (8 erros) | sim | Canário: `rac--completo` com `Procedure.identifier` fora da máscara da invariante cmd-1; precisa reprovar nos dois. | `8d7032ffbf5c7a14` |
| `canarios/redfm--canario-dfm-2.json` | [reprovado](mensagens/redfm--canario-dfm-2.camada4.txt) (2 erros) | [reprovado](mensagens/redfm--canario-dfm-2.validador-local.txt) (3 erros) | sim | Canário: `redfm--receita-embutida` sem `MedicationDispense.quantity`, que a invariante dfm-2 exige; precisa reprovar nos dois. | `cf7107c7a6bd3f80` |
| `canarios/ria-r--canario-sem-route.json` | [reprovado](mensagens/ria-r--canario-sem-route.camada4.txt) (2 erros) | [reprovado](mensagens/ria-r--canario-sem-route.validador-local.txt) (3 erros) | sim | Canário: `ria-r--minimo` sem `Immunization.route`, 1..1 no perfil; precisa reprovar nos dois. | `87b371c55f8573bc` |
| `canarios/rira--canario-sem-event.json` | [reprovado](mensagens/rira--canario-sem-event.camada4.txt) (1 erro) | [reprovado](mensagens/rira--canario-sem-event.validador-local.txt) (1 erro) | sim | Canário: `rira--um-evento` sem `Composition.event`, 1..* no perfil; precisa reprovar nos dois. | `17bff81c4eb84909` |

## Controle: exemplo oficial

| Arquivo | Camada 4 | Validador Local | Iguais | Observação | SHA-256 (16) |
|---|---|---|---|---|---|
| `controle/ria-r--ExemploJSONPreenchidoRIAR.json` | [reprovado](mensagens/ria-r--ExemploJSONPreenchidoRIAR.camada4.txt) (3 erros) | [aprovado](mensagens/ria-r--ExemploJSONPreenchidoRIAR.validador-local.txt) (0 erros) | **não** | Divergência conhecida. O exemplo oficial usa urn:uuid:transient-N, que não é UUID (defeito nº 6). Só a Camada 4 reprova. | `0ef5cec4f965ec10` |

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
