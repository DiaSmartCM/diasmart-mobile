# -*- coding: utf-8 -*-
"""Reprise de la mise en page de la planche."""
import io
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
F = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema_rebonds.py")
s = io.open(F, encoding="utf-8").read()


def rep(a, b, nom):
    global s
    if a not in s:
        raise SystemExit("INTROUVABLE (%s)" % nom)
    s = s.replace(a, b, 1)
    print("ok :", nom)


rep("fig = plt.figure(figsize=(15, 10.5), dpi=110)",
    "fig = plt.figure(figsize=(15, 11.6), dpi=110)", "hauteur")

rep("""grille = fig.add_gridspec(2, 2, height_ratios=[1.15, 1],
                          hspace=0.30, wspace=0.20,
                          left=0.065, right=0.975, top=0.885, bottom=0.075)""",
    """grille = fig.add_gridspec(2, 2, height_ratios=[1.0, 1.05],
                          hspace=0.34, wspace=0.18,
                          left=0.065, right=0.975, top=0.865, bottom=0.055)""",
    "grille")

rep('fig.text(0.065, 0.955, "Modèle', 'fig.text(0.065, 0.958, "Modèle', "titre")
rep("fig.text(0.065, 0.922,", "fig.text(0.065, 0.926,", "sous-titre")
rep("fontsize=13, pad=12, color=TEXTE)", "fontsize=13, pad=18, color=TEXTE)", "titre du graphe")

rep("""y = 0.90
for titre, formule, valeur in lignes:
    ax2.text(0.045, y, titre, fontsize=10.5, color=SOURDINE, transform=ax2.transAxes)
    ax2.text(0.045, y - 0.072, formule, fontsize=15.5, color=TEXTE, transform=ax2.transAxes)
    if valeur:
        ax2.text(0.955, y - 0.066, valeur, fontsize=11, color=INDIGO,
                 ha="right", transform=ax2.transAxes, family="monospace")
    y -= 0.158""",
    """# Le libelle et sa formule forment un bloc ; l'espace entre deux blocs
# doit rester superieur a la hauteur rendue d'une formule, sinon le titre
# suivant vient mordre dessus.
y = 0.925
for titre, formule, valeur in lignes:
    ax2.text(0.05, y, titre, fontsize=10, color=SOURDINE, transform=ax2.transAxes)
    ax2.text(0.05, y - 0.062, formule, fontsize=14, color=TEXTE,
             transform=ax2.transAxes, va="center")
    if valeur:
        ax2.text(0.95, y - 0.062, valeur, fontsize=10.5, color=INDIGO,
                 ha="right", va="center", transform=ax2.transAxes, family="monospace")
    y -= 0.152""",
    "panneau des formules")

rep("barres = ax3.inset_axes([0.06, 0.30, 0.40, 0.42])",
    "barres = ax3.inset_axes([0.07, 0.42, 0.36, 0.34])", "graphe a barres")

rep("ax3.text(0.52, 0.63,", "ax3.text(0.50, 0.74,", "legende du facteur")
rep("fontsize=11.5, color=TEXTE, transform=ax3.transAxes, va=\"top\")",
    "fontsize=12, color=TEXTE, transform=ax3.transAxes, va=\"top\")", "taille de la legende")

rep("ax3.text(0.045, 0.235,", "ax3.text(0.05, 0.31,", "paragraphe")
rep('''         "Deux contraintes incompatibles ont été imposées :\\n"
         "la vitesse doit se raccorder à l’impact, et les six\\n"
         "rebonds doivent tenir dans 1550 ms.\\n\\n"
         "Avec la gravité de la chute, la séquence durerait\\n"
         f"{(2*v0/g_chute) * ((1-REST**N)/(1-REST)):.0f} ms. La bille tombe donc doucement\\n"
         "et rebondit sèchement.",
         fontsize=10.8, color=SOURDINE, transform=ax3.transAxes, va="top", linespacing=1.55)''',
    '''         "Deux contraintes incompatibles : la vitesse doit se raccorder\\n"
         "à l’impact, et les six rebonds doivent tenir dans 1550 ms.\\n\\n"
         "Avec la gravité de la chute, la séquence durerait\\n"
         f"{(2*v0/g_chute) * ((1-REST**N)/(1-REST)):.0f} ms : la bille tombe doucement, puis rebondit sèchement.",
         fontsize=10.5, color=SOURDINE, transform=ax3.transAxes, va="top", linespacing=1.5)''',
    "texte du paragraphe")

io.open(F, "w", encoding="utf-8").write(s)
print("\nmise en page reprise")
