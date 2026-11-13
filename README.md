# RNDS-BR Connect

[![Testes](https://github.com/IgMiras/rnds-br-connect/actions/workflows/testes.yml/badge.svg)](https://github.com/IgMiras/rnds-br-connect/actions/workflows/testes.yml)

Framework em Java 17 que prepara documentos clínicos no padrão HL7 FHIR conformes aos perfis da Rede Nacional de Dados em Saúde (RNDS), a partir dos dados de um sistema de saúde e no vocabulário desse sistema. O sistema cliente informa os seus códigos locais, e o framework confere as regras de negócio dos manuais de integração, traduz cada código para a terminologia nacional por meio de estratégias substituíveis, registra a origem de cada tradução, monta o documento e o valida localmente contra os artefatos oficiais de conformidade.

## Escopo

Os seis serviços com manual de integração publicado pelo DATASUS, em sete documentos:

| Documento | Serviço |
|---|---|
| RIA-R | Registro de Imunobiológico Administrado, em rotina |
| RIA-C | Registro de Imunobiológico Administrado, em campanha |
| REL | Resultado de Exame Laboratorial |
| RIRA | Registro de Informação de Regulação Assistencial |
| REDFM | Registro Eletrônico da Prescrição, Dispensação ou Fornecimento de Medicamentos (dispensação e fornecimento) |
| SA | Sumário de Alta |
| RAC | Registro de Atendimento Clínico |

**O framework não envia documentos à RNDS.** A camada de transporte, com autenticação mTLS por certificado ICP-Brasil, está projetada, mas não implementada: o trabalho não teve acesso a certificado nem a credenciamento no ambiente de homologação.

## Requisitos e compilação

Java 17 e Maven 3. Na raiz do repositório:

```bash
mvn clean install
```

O comando compila o projeto, roda os 221 testes e instala o artefato no repositório Maven local. O projeto ainda não está no Maven Central; depois de instalar, declare a dependência:

```xml
<dependency>
  <groupId>br.rndsconnect</groupId>
  <artifactId>rnds-br-connect-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Uso

A Facade é construída uma vez, na inicialização, e é imutável:

```java
RndsIntegrationFacade facade = RndsFramework.builder()
        .solicitanteId("29376")                  // fornecido pela RNDS no credenciamento
        .servico(Servico.RIA, "conformance")     // só os serviços declarados são carregados
        .mapping(new YamlMappingSource(TerminologyDomain.VACINA, "mapeamentos/vacinas.yml"))
        // ... uma fonte para cada domínio terminológico que o documento usa
        .build();

DocumentoPreparado documento = facade.prepararImunizacaoRotina(dto);
// documento.bundle(): o Bundle FHIR validado; documento.trilha(): a origem de cada tradução
```

As tabelas de correspondência entre os códigos locais e os da RNDS são da instituição que integra, e a correspondência clínica deve ser revisada por profissional de saúde. As tabelas em `rnds-br-connect-core/src/test/resources/mapeamentos/` existem para os testes e não são tabelas clínicas validadas.

### Catálogos dos códigos aceitos

O pacote `br.rndsconnect.catalogo` traz as listas dos códigos que a RNDS aceita em dois elementos, extraídas dos artefatos oficiais: os 475 nomes de exame do REL e as 23 medidas observadas do RAC. Para conferir a tabela da instituição contra o catálogo ao subir a aplicação:

```java
MappingSource exames = new YamlMappingSource(TerminologyDomain.NOME_EXAME, "mapeamentos/exames.yml");
new MapeamentoConformeCatalogo(CatalogoCodigosAceitos.exames()).exigirConformidade(exames);
```

A conferência interrompe a inicialização e lista todas as linhas que apontam para um código não aceito. Ela diz se o código é aceito, e não se é o código clinicamente certo. Os demais domínios terminológicos ainda não têm catálogo.

Os pacotes de conformidade foram recortados das distribuições oficiais espelhadas em [IgMiras/rnds-artefatos](https://github.com/IgMiras/rnds-artefatos); o `index.txt` de cada pacote registra de qual distribuição ele foi copiado.

## Conformidade verificada

Todo documento passa pela Camada 4, a validação local do framework, antes de sair da Facade, e a suíte confere isso a cada execução. Como verificação externa, os documentos dos sete construtores, inclusive as variações de campos opcionais, foram submetidos também ao Validador Local da RNDS, o aplicativo que o DATASUS distribui aos integradores. Os arquivos, as mensagens dos dois validadores e a tabela dos veredictos estão em [`evidencias/validador-local/`](evidencias/validador-local/), e o script que os gera, em `scripts/evidencias-validador-local.py`.

## Licença e autoria

Copyright 2026 Igor Mendes Domingues Miras. Distribuído sob a [Licença Apache 2.0](LICENSE); veja também o [NOTICE](NOTICE).

Desenvolvido como Trabalho de Conclusão de Curso do Bacharelado em Ciência da Computação da FCT/UNESP, Presidente Prudente, sob orientação do Prof. Dr. Ronaldo Celso Messias Correia.

Os artefatos de conformidade em `rnds-br-connect-core/src/main/resources/conformance/` e o exemplo oficial em `evidencias/validador-local/controle/` são publicados pelo Ministério da Saúde (DATASUS) e não são cobertos por esta licença.

## Como citar

Use o botão **Cite this repository**, na lateral da página do repositório, ou os metadados do [CITATION.cff](CITATION.cff).
