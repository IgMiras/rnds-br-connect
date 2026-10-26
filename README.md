# RNDS-BR Connect

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

O comando compila os dois módulos, roda os testes (181 no core e 9 no `rnds-br-connect-exames-starter`) e instala os artefatos no repositório Maven local. O projeto ainda não está no Maven Central; depois de instalar, declare a dependência:

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

Os pacotes de conformidade foram recortados das distribuições oficiais espelhadas em [IgMiras/rnds-artefatos](https://github.com/IgMiras/rnds-artefatos); o `index.txt` de cada pacote registra de qual distribuição ele foi copiado.

## Licença e autoria

Copyright 2026 Igor Mendes Domingues Miras. Distribuído sob a [Licença Apache 2.0](LICENSE); veja também o [NOTICE](NOTICE).

Desenvolvido como Trabalho de Conclusão de Curso do Bacharelado em Ciência da Computação da FCT/UNESP, Presidente Prudente, sob orientação do Prof. Dr. Ronaldo Celso Messias Correia.

Os artefatos de conformidade em `rnds-br-connect-core/src/main/resources/conformance/` são publicados pelo Ministério da Saúde (DATASUS) e não são cobertos por esta licença.

## Como citar

Use o botão **Cite this repository**, na lateral da página do repositório, ou os metadados do [CITATION.cff](CITATION.cff).
