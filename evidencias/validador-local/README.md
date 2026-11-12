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

Os dois validadores usam o mesmo motor, o HAPI FHIR **5.1.0**: a Camada 4 foi migrada para a
versão que o aplicativo embute, para que o veredicto dela venha do mesmo motor da ferramenta
que o integrador é mandado usar. A comparação é, portanto, uma prova de **paridade**, e não
uma segunda opinião independente. O que ainda difere entre os dois é:

- o **pacote**: a Camada 4 carrega um recorte de cada distribuição, alterado na carga, com o
  snapshot de cada perfil já gravado, e o Validador Local carrega a distribuição inteira e
  calcula os snapshots na hora. Os dois snapshots saem do mesmo gerador, o do HAPI FHIR 5.1.0;
  os do framework foram gerados pelo `scripts/gerar-snapshots.sh` em 12/11/2026;
- a **configuração** da cadeia de validação, próxima, mas não idêntica;
- o **tratamento das mensagens**: depois de validar, o aplicativo promove a erro toda mensagem,
  de qualquer severidade, que não seja aviso de vínculo `extensible`, informação de vínculo
  `preferred` ou aviso de referência sem `display` (`fhir.val.ValidarItemCallable`, no
  bytecode do aplicativo). A Camada 4 segue o critério do HAPI: só erro e fatal reprovam.

Não é independência de implementação: um erro do HAPI FHIR 5.1.0 passaria pelos dois sem
divergência.

Até o commit `ba06025`, a Camada 4 usava o HAPI FHIR 7.6.0, e a independência era também de
versão do motor. Com a 7.6.0 havia uma segunda divergência, o exemplo oficial do RIA-R, com
`urn:uuid:transient-N` (defeito nº 6), que só a Camada 4 reprovava. Ela vinha da versão e
sumiu com a migração: os dois validadores aprovam o controle.

Os canários mostram que cada validador está, de fato, aplicando o pacote: os quatro precisam
ser reprovados pelos dois.

## Divergência conhecida

| Arquivo | Camada 4 | Validador Local | Causa |
|---|---|---|---|
| `documentos/redfm--variacao-2-substituicao.json` | aprova | reprova | O vínculo required de `Composition.relatesTo.code`, herdado do `br-core-composition`, aponta para uma página de documentação, e não para um `ValueSet` (defeito nº 12). Os dois emitem a mesma mensagem, `ValueSet ... not found by validator`, como aviso. O aplicativo a promove a erro, pela regra descrita acima. |

A causa foi isolada em 12/11/2026. Com a cadeia e as opções do aplicativo, a Camada 4
continua emitindo aviso e aprovando; numa sonda com essa cadeia e os 401 arquivos XML da
distribuição do REDFM, também. A regra de promoção do aplicativo, aplicada às mensagens da
Camada 4, reproduz o veredicto dele em 33 dos 38 arquivos; nos outros cinco, a Camada 4 emite
um aviso a mais, de um `ValueSet` de vínculo `preferred` que ficou fora do recorte do SA e do
RAC. A diferença não é de versão nem de configuração, e sim do tratamento das mensagens. A
divergência vem de um defeito do artefato oficial, e qual veredicto o barramento da RNDS dá,
só o envio diria.
