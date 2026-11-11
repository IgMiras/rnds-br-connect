# Evidências: Camada 4 × Validador Local da RNDS

Esta pasta guarda a conferência externa do framework: **o mesmo arquivo submetido aos dois
validadores**, a Camada 4 do framework e o Validador Local da RNDS, o aplicativo que o DATASUS
distribui aos integradores. O resultado está em [`RESULTADOS.md`](RESULTADOS.md).

A suíte de testes garante, a cada execução, que os documentos dos sete construtores passam na
Camada 4. O que esta pasta acrescenta é o veredicto da ferramenta do Ministério da Saúde sobre
esses mesmos documentos, com os arquivos e as mensagens que o sustentam.

## O que há aqui

| Pasta ou arquivo | Conteúdo | Origem |
|---|---|---|
| `documentos/` | Os Bundles dos sete construtores: os documentos de referência, os da verificação externa e as 21 variações de opcionais | Gerados pela suíte |
| `canarios/` | Quatro documentos alterados para violar o perfil, um por pacote de conformidade | Gerados pelo script a partir de `documentos/` |
| `controle/` | O exemplo oficial do RIA-R, `ExemploJSONPreenchidoRIAR.json` | Portal de Serviços do DATASUS; não coberto pela licença do projeto (ver [NOTICE](../../NOTICE)) |
| `mensagens/` | Para cada arquivo, as mensagens da Camada 4 (`.camada4.txt`) e as do Validador Local (`.validador-local.txt`) | Geradas pelo script |
| `RESULTADOS.md` | A tabela dos veredictos, com o commit, as versões dos dois validadores e o SHA-256 de cada arquivo | Gerado pelo script |

Os nomes dos arquivos começam pela sigla do documento (`ria-r`, `ria-c`, `rel`, `rira`, `redfm`,
`sa`, `rac`) seguida de `--`. É pela sigla que o script escolhe o pacote de cada validador.

## Como reproduzir

São necessários Java 17, Maven 3 e Python 3, além de duas coisas que este repositório não
distribui:

- o **Validador Local da RNDS** (`SOA-RNDS_ValidadorLocal_Aplicativo`), obtido no
  [Portal de Serviços do DATASUS](https://servicos-datasus.saude.gov.br/);
- um clone de **[IgMiras/rnds-artefatos](https://github.com/IgMiras/rnds-artefatos)**, com as
  distribuições oficiais dos arquivos de definição.

```bash
python3 scripts/evidencias-validador-local.py \
    --validador-local <pasta rnds-fhir-validador, a que contém libs/> \
    --artefatos <clone de rnds-artefatos>
```

O script apaga e refaz `documentos/`, `canarios/`, `mensagens/` e `RESULTADOS.md`, e sai com
erro se aparecer uma divergência que não esteja registrada como conhecida. Os documentos são
determinísticos, porque a suíte fixa os UUIDs e o relógio: uma nova execução sobre o mesmo
commit reproduz os mesmos arquivos e os mesmos SHA-256.

## Como cada validador é chamado

- **Camada 4:** `scripts/CamadaQuatroEmArquivos.java` carrega o pacote de conformidade do
  serviço, o mesmo que a Facade carrega, e valida cada arquivo com a `LocalFhirValidator`. Só
  as severidades erro e fatal reprovam.
- **Validador Local:** `scripts/ValidadorLocalSemJanela.java` chama
  `fhir.val.ExecutarValidacao.executar`, o método que a janela do aplicativo chama ao clicar em
  "Validar", com os mesmos três parâmetros: a pasta dos arquivos de definição, o arquivo JSON e
  o arquivo de mensagens. A pasta é a distribuição inteira do serviço, como o DATASUS a
  distribui. O aplicativo não é alterado.

## O que a comparação prova, e o que não prova

Os dois validadores usam a mesma biblioteca, o HAPI FHIR, em versões diferentes (7.6.0 na
Camada 4, 5.1.0 no aplicativo), e com configurações próximas, mas não idênticas. A
independência entre eles é de **pacote**, porque a Camada 4 carrega um recorte de cada
distribuição, alterado na carga, e o Validador Local carrega a distribuição inteira, e de
**versão e configuração** do motor. Não é independência de implementação: um erro do HAPI
FHIR presente nas duas versões passaria pelos dois sem divergência.

Os canários mostram que cada validador está, de fato, aplicando o pacote: os quatro precisam
ser reprovados pelos dois. O controle mostra uma divergência já documentada, em que só a
Camada 4 reprova.

## Divergências conhecidas

| Arquivo | Camada 4 | Validador Local | Causa |
|---|---|---|---|
| `controle/ria-r--ExemploJSONPreenchidoRIAR.json` | reprova | aprova | `urn:uuid:transient-N` não é UUID (defeito nº 6) |
| `documentos/redfm--variacao-2-substituicao.json` | aprova | reprova | O vínculo required de `Composition.relatesTo.code`, herdado do `br-core-composition`, aponta para uma página de documentação, e não para um `ValueSet` (defeito nº 12). A Camada 4 trata o `ValueSet` não encontrado como aviso; o Validador Local, como erro. |

Nenhuma das duas pode ser resolvida nos validadores: as duas vêm de defeitos dos artefatos
oficiais, e qual veredicto o barramento da RNDS dá, só o envio diria.
