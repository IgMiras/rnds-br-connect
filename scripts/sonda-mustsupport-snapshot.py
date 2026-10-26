#!/usr/bin/env python3
"""Sonda do item 10: quanto o differential esconde de MustSupport.

SO MEDE. Nao altera arquivo nenhum, nao roda teste, nao toca no portao.

Para cada perfil instanciado dos sete servicos, conta os elementos
MustSupport com max != 0 no `differential` e no `snapshot`, e mostra a
diferenca. A lista de servicos e de perfis e LIDA do proprio
AuditoriaEmissao.java, para nao divergir dele.

Nenhum StructureDefinition da RNDS e publicado com snapshot: quem o tem foi
gerado por scripts/gerar-snapshots.sh e gravado de volta no arquivo. Perfil
sem snapshot aparece como SEM SNAPSHOT -- e esse e o dado que decide se a
troca da auditoria e possivel hoje ou se depende de gerar snapshot antes.

  uso: python3 sonda-mustsupport-snapshot.py
       (a partir da raiz do repositorio)
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://hl7.org/fhir}"
RAIZ = "rnds-br-connect-core/src/main/resources"
AUDITORIA = "rnds-br-connect-core/src/test/java/br/rndsconnect/tools/AuditoriaEmissao.java"


def servicos_do_java():
    """Le o mapa SERVICOS do AuditoriaEmissao.java. Fonte unica, sem copia."""
    if not os.path.exists(AUDITORIA):
        sys.exit("ERRO: rode a partir da raiz do repositorio. Nao achei %s" % AUDITORIA)
    txt = open(AUDITORIA, encoding="utf-8").read()
    i = txt.index("SERVICOS = Map.of(")
    # Fecha no ponto-e-virgula que encerra a DECLARACAO, nao no primeiro
    # ")));" -- cortar ali amputa a ultima entrada do mapa, e a sonda
    # deixaria um servico inteiro de fora sem dizer nada. Foi o que ocorreu
    # na primeira versao, com o RAC: o mesmo erro de "nao ha" contra "nao vi"
    # que a Secao 8.12 registra.
    fim = txt.index(";", i)
    bloco = txt[i:fim]
    out = []
    for m in re.finditer(r'"([A-Z\-]+)",\s*new Servico\(\s*"([^"]+)",\s*List\.of\((.*?)\)\)', bloco, re.S):
        perfis = re.findall(r'"([^"]+)"', m.group(3))
        out.append((m.group(1), m.group(2), perfis))
    # Guarda contra leitura parcial: o Java declara os sete servicos do escopo.
    esperados = txt[i:fim].count("new Servico(")
    if len(out) != esperados:
        sys.exit("ERRO: li %d servicos mas o Java declara %d. A sonda nao vai "
                 "relatar numero parcial como se fosse o total." % (len(out), esperados))
    return out


def indice(diretorio):
    p = os.path.join(RAIZ, diretorio, "index.txt")
    if not os.path.exists(p):
        return None
    return [l.strip() for l in open(p, encoding="utf-8") if l.strip() and not l.startswith("#")]


CACHE = {}


def perfis_do_pacote(diretorio):
    """url -> root do StructureDefinition, para o pacote inteiro."""
    if diretorio in CACHE:
        return CACHE[diretorio]
    idx = indice(diretorio)
    if idx is None:
        CACHE[diretorio] = None
        return None
    mapa = {}
    for rel in idx:
        if not rel.lower().endswith(".xml"):
            continue
        try:
            r = ET.parse(os.path.join(RAIZ, diretorio, rel)).getroot()
        except Exception:
            continue
        if r.tag != NS + "StructureDefinition":
            continue
        u = r.find(NS + "url")
        if u is not None and u.get("value"):
            mapa[u.get("value")] = r
    CACHE[diretorio] = mapa
    return mapa


def achar(diretorio, nome):
    mapa = perfis_do_pacote(diretorio)
    if mapa is None:
        return None
    for url, sd in mapa.items():
        if url.endswith("/" + nome):
            return sd
    return None


def contar(sd, parte):
    """MustSupport com max != 0. None se a parte nao existe."""
    bloco = sd.find(NS + parte)
    if bloco is None:
        return None, []
    n, ids = 0, []
    for el in bloco.findall(NS + "element"):
        ms = el.find(NS + "mustSupport")
        mx = el.find(NS + "max")
        if ms is not None and ms.get("value") == "true" and not (mx is not None and mx.get("value") == "0"):
            n += 1
            ids.append(el.get("id") or "")
    return n, ids


def base(sd):
    b = sd.find(NS + "baseDefinition")
    return b.get("value").rsplit("/", 1)[-1] if b is not None and b.get("value") else "—"


def main():
    print("=" * 104)
    print("SONDA DO ITEM 10 — MustSupport no differential contra o snapshot")
    print("=" * 104)
    print("%-7s %-54s %6s %6s %7s  %s" % ("serv", "perfil", "diff", "snap", "delta", "baseDefinition"))
    print("-" * 104)

    tot_d = tot_s = 0
    sem_snapshot = []
    sem_pacote = []
    grandes = []

    for serv, diretorio, perfis in servicos_do_java():
        for nome in perfis:
            sd = achar(diretorio, nome)
            if sd is None:
                sem_pacote.append("%s/%s" % (serv, nome))
                print("%-7s %-54s %6s %6s %7s  pacote ou perfil ausente (%s)"
                      % (serv, nome[:54], "-", "-", "-", diretorio))
                continue
            cd, _ = contar(sd, "differential")
            cs, ids = contar(sd, "snapshot")
            cd = cd or 0
            tot_d += cd
            if cs is None:
                sem_snapshot.append("%s/%s" % (serv, nome))
                tot_s += cd
                print("%-7s %-54s %6d %6s %7s  SEM SNAPSHOT (%s)"
                      % (serv, nome[:54], cd, "-", "-", base(sd)))
            else:
                tot_s += cs
                d = cs - cd
                if d >= 4:
                    grandes.append((serv, nome, cd, cs, base(sd), ids))
                print("%-7s %-54s %6d %6d %+7d  %s" % (serv, nome[:54], cd, cs, d, base(sd)))

    print("-" * 104)
    print("TOTAL  differential: %d   |   snapshot (differential onde falta): %d   |   delta: %+d"
          % (tot_d, tot_s, tot_s - tot_d))
    print()
    print("Perfis SEM snapshot: %d  %s" % (len(sem_snapshot), ", ".join(sem_snapshot) or "-"))
    print("Perfis/pacotes ausentes: %d  %s" % (len(sem_pacote), ", ".join(sem_pacote) or "-"))
    print()
    print("O delta e piso: onde falta snapshot, a contagem usou o differential, que e menor.")
    print("Gerar o snapshot dos %d perfis acima aumentaria o total ainda mais." % len(sem_snapshot))

    if grandes:
        print()
        print("=" * 104)
        print("ONDE O DIFFERENTIAL MAIS ESCONDE (delta >= 4)")
        print("=" * 104)
        for serv, nome, cd, cs, bd, ids in grandes:
            print("\n%s · %s: %d -> %d  (herda de %s)" % (serv, nome, cd, cs, bd))
            for i in ids[:40]:
                print("      %s" % i)
            if len(ids) > 40:
                print("      ... e %d outros" % (len(ids) - 40))


if __name__ == "__main__":
    main()
