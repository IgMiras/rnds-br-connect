#!/usr/bin/env python3
"""
Extrai a tabela "de/para" da RN1 do Manual de Integracao do REL.

    uso: python3 scripts/extrair-rn1-manual-rel.py <RELManualDeIntegracaoDaRNDS.pdf>

A RN1 (secao 7.2, tabela no Anexo secao 12) diz:

    "Cada tipo de interpretacao esta condicionado ao tipo de diagnostico
     laboratorial realizado de acordo com o anexo tabela regra de negocio"

A tabela associa cada codigo LOINC ao CodeSystem BRTipoResultado* de onde o
resultado qualitativo daquele exame DEVE vir. A coluna "Code" traz "*",
indicando que qualquer codigo daquele CodeSystem serve.

CUIDADO NA EXTRACAO: o Anexo contem DUAS tabelas com formato identico, RN1 e
RN2, e a segunda comeca sem quebra visual clara. Extrair o Anexo inteiro
mistura as duas. Alem disso, um regex ingenuo trunca codigos LOINC de 6
digitos ("100891-1" vira "00891-1"), por isso o lookbehind.
"""
import sys, re, subprocess, json, collections

TITULO_RN1 = 'Tabela “de/para” resultado laboratorial da RN1'
TITULO_RN2 = 'Tabela “de/para” resultado laboratorial da RN2'
CS = "http://www.saude.gov.br/fhir/r4/CodeSystem/"
# lookbehind impede casar o sufixo de um codigo de 6 digitos
PADRAO = r'(?<![\d-])(\d{4,6}-\d)\s+([A-Z]+)\s'


def main(pdf):
    txt = subprocess.run(["pdftotext", "-layout", pdf, "-"],
                         capture_output=True, text=True, check=True).stdout
    i1, i2 = txt.find(TITULO_RN1), txt.find(TITULO_RN2)
    if i1 < 0:
        raise SystemExit("ERRO: tabela da RN1 nao encontrada no PDF.")
    if i2 < 0:
        print("AVISO: tabela da RN2 nao encontrada; extraindo ate o fim do documento.")
        i2 = len(txt)

    tabela = {}
    for cod, sigla in re.findall(PADRAO, txt[i1:i2]):
        tabela[cod] = sigla

    por_tipo = collections.Counter(tabela.values())
    linhas = [
        "# Tabela de/para da RN1 do Manual de Integracao do REL.",
        "#",
        "# GERADO por scripts/extrair-rn1-manual-rel.py. NAO EDITAR A MAO.",
        f"# Fonte: {pdf.split('/')[-1]}, secao 7.2 e Anexo (secao 12).",
        "#",
        '# RN1: "Cada tipo de interpretacao esta condicionado ao tipo de diagnostico',
        '#       laboratorial realizado de acordo com o anexo tabela regra de negocio"',
        "#",
        "# Para cada codigo LOINC, o CodeSystem de onde o resultado qualitativo deve vir.",
        f"# {len(tabela)} codigos cobertos: " +
        ", ".join(f"{v} {k}" for k, v in por_tipo.most_common()),
        "#",
        "# COBERTURA PARCIAL: dos 442 codigos LOINC aceitos pela RNDS, a RN1 se",
        "# pronuncia sobre estes. Para os demais, a regra nao diz nada, e o",
        "# validador deve ACEITAR — silencio do manual nao e proibicao.",
        "",
        "regra: RN1",
        "codigos:",
    ]
    for cod in sorted(tabela):
        linhas.append(f'  "{cod}": {CS}BRTipoResultado{tabela[cod]}')

    destino = "rnds-br-connect-core/src/main/resources/regras/rn1-interpretacao-por-exame.yml"
    import os
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    open(destino, "w", encoding="utf-8").write("\n".join(linhas) + "\n")

    print(f"{len(tabela)} codigos gravados em {destino}")
    for k, v in por_tipo.most_common():
        print(f"   BRTipoResultado{k}: {v}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
