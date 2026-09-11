# -*- coding: utf-8 -*-
"""
Planche du modele de rebonds de l'ecran de demarrage.

Les courbes ne sont pas dessinees a la main : elles sont calculees avec les
constantes reelles de SplashScreen.kt. Si une constante change ici, la
planche se trompera exactement comme se tromperait l'animation — c'est
voulu, c'est ce qui en fait une verification et pas une illustration.
"""

import os
import sys

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

sys.stdout.reconfigure(encoding="utf-8")

# ── Constantes, identiques a SplashScreen.kt ──────────────────────────
REST = 0.74
T_MARQUE, T_CHUTE = 800.0, 1350.0
T_REBONDS_FIN, T_POSE = 2900.0, 3700.0
SOL, R_BILLE = 470.0, 34.0
SOL_BILLE = SOL - R_BILLE            # 436 : centre de la bille posee
DEPART_Y = 96.0 + 180.0 - 24.0       # 252 : sortie du bas de la marque
N = 6

# ── Derivations ───────────────────────────────────────────────────────
chute_h = SOL_BILLE - DEPART_Y                       # 184 px
T_chute = T_CHUTE - T_MARQUE                         # 550 ms
v_impact = 2 * chute_h / T_chute
g_chute = 2 * chute_h / T_chute ** 2

durees = np.array([REST ** k for k in range(N)])
durees = durees / durees.sum() * (T_REBONDS_FIN - T_CHUTE)
T0 = durees[0]
h0 = min(REST * v_impact * T0 / 4, 74.0)
hauteurs = h0 * REST ** (2 * np.arange(N))
v0 = REST * v_impact
g_rebonds = 2 * v0 / T0

print(f"v_impact   {v_impact:.3f} px/ms")
print(f"T0 {T0:.0f} ms   h0 {h0:.1f} px")
print(f"g chute    {g_chute*1000:.3f} e-3   g rebonds {g_rebonds*1000:.3f} e-3"
      f"   rapport {g_rebonds/g_chute:.2f}")

# ── Palette ───────────────────────────────────────────────────────────
NUIT, PANNEAU = "#0D0B2E", "#151041"
TRAIT, TEXTE, SOURDINE = "#2A2160", "#E9E7F7", "#9B93C8"
BLEU, VIOLET, CORAIL, INDIGO = "#2C86FF", "#7B45F0", "#FF6B8A", "#8B93F0"

plt.rcParams.update({
    "figure.facecolor": NUIT, "axes.facecolor": PANNEAU,
    "axes.edgecolor": TRAIT, "axes.labelcolor": SOURDINE,
    "xtick.color": SOURDINE, "ytick.color": SOURDINE,
    "text.color": TEXTE, "font.size": 11,
    "axes.grid": True, "grid.color": TRAIT, "grid.linewidth": 0.6,
})

fig = plt.figure(figsize=(15, 11.6), dpi=110)
grille = fig.add_gridspec(2, 2, height_ratios=[1.0, 1.05],
                          hspace=0.34, wspace=0.18,
                          left=0.065, right=0.975, top=0.865, bottom=0.055)

fig.text(0.065, 0.958, "Modèle de rebonds de l’écran de démarrage",
         fontsize=23, fontweight="bold")
fig.text(0.065, 0.926,
         "Courbes calculées avec les constantes de SplashScreen.kt. "
         "Hauteurs en unités du repère 400 × 780, temps en millisecondes.",
         fontsize=11.5, color=SOURDINE)

# ── A. Hauteur en fonction du temps ───────────────────────────────────
ax = fig.add_subplot(grille[0, :])
LETTRES = ["t", "r", "a", "m", "S", "a", "D"]

t = np.linspace(T_MARQUE, T_CHUTE, 200)
p = (t - T_MARQUE) / T_chute
ax.plot(t, SOL_BILLE - (DEPART_Y + chute_h * p ** 2) + 0,
        color=CORAIL, lw=2.6, label="Chute libre  $y = y_0 + \\Delta h\\,u^2$")

debut = T_CHUTE
for k in range(N):
    tt = np.linspace(debut, debut + durees[k], 160)
    u = (tt - debut) / durees[k]
    ax.plot(tt, 4 * hauteurs[k] * u * (1 - u),
            color=BLEU if k == 0 else INDIGO, lw=2.4,
            label="Arc balistique  $y = 4h_k\\,u(1-u)$" if k == 0 else None)
    ax.annotate(f"{hauteurs[k]:.0f}", (debut + durees[k] / 2, hauteurs[k]),
                textcoords="offset points", xytext=(0, 7), ha="center",
                fontsize=9.5, color=INDIGO)
    ax.annotate(f"{durees[k]:.0f} ms", (debut + durees[k] / 2, -7),
                ha="center", fontsize=8.5, color=SOURDINE)
    debut += durees[k]

tt = np.linspace(T_REBONDS_FIN, T_POSE, 200)
u = (tt - T_REBONDS_FIN) / (T_POSE - T_REBONDS_FIN)
cible = SOL_BILLE - (SOL - 46 * 0.80)
ax.plot(tt, (1 - u) * 0 + u * cible + 4 * 42 * u * (1 - u),
        color=VIOLET, lw=2.6, label="Vol final vers le « i »")

for k, lettre in enumerate(LETTRES):
    x = T_CHUTE + durees[:k].sum()
    ax.plot([x], [0], "o", ms=7, color=BLEU, zorder=5)
    ax.annotate(lettre, (x, 0), textcoords="offset points", xytext=(0, -21),
                ha="center", fontsize=13, fontweight="bold", color=BLEU)

ax.axhline(0, color=SOURDINE, lw=1.1, alpha=0.55)
ax.set_xlabel("temps (ms)")
ax.set_ylabel("hauteur au-dessus de la ligne de base (px)")
ax.set_ylim(-34, 200)
ax.set_title("Un contact, une lettre  —  l’apogée décroît en $e^{2k}$, la durée en $e^{k}$",
             fontsize=13, pad=18, color=TEXTE)
ax.legend(facecolor=PANNEAU, edgecolor=TRAIT, labelcolor=TEXTE, fontsize=10.5, loc="upper right")

# ── B. Les formules ───────────────────────────────────────────────────
ax2 = fig.add_subplot(grille[1, 0])
ax2.axis("off")
ax2.add_patch(FancyBboxPatch((0.005, 0.01), 0.99, 0.98,
                             boxstyle="round,pad=0.018,rounding_size=0.03",
                             facecolor=PANNEAU, edgecolor=TRAIT, lw=1.2,
                             transform=ax2.transAxes))

lignes = [
    ("Vitesse d’arrivée de la chute", r"$v_{impact}=\dfrac{2\,\Delta h}{T}$",
     f"= {v_impact:.3f} px/ms"),
    ("Vitesse de départ d’un arc", r"$v=\dfrac{4h}{T}$", "identité newtonienne"),
    ("Restitution à chaque choc", r"$v_k=e^{\,k+1}\,v_{impact}$", f"e = {REST}"),
    ("Apogées et durées", r"$h_k=h_0\,e^{2k}\qquad T_k=T_0\,e^{k}$", ""),
    ("Premier arc, budget imposé", r"$T_0=T_{b}\,\dfrac{1-e}{1-e^{n}}$", f"= {T0:.0f} ms"),
    ("Apogée raccordée à l’impact", r"$h_0=\dfrac{e\,v_{impact}\,T_0}{4}$", f"= {h0:.1f} px"),
]
# Le libelle et sa formule forment un bloc ; l'espace entre deux blocs
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
    y -= 0.152

# ── C. La limite du modele ────────────────────────────────────────────
ax3 = fig.add_subplot(grille[1, 1])
ax3.axis("off")
ax3.add_patch(FancyBboxPatch((0.005, 0.01), 0.99, 0.98,
                             boxstyle="round,pad=0.018,rounding_size=0.03",
                             facecolor="#2A1233", edgecolor="#6B2E52", lw=1.4,
                             transform=ax3.transAxes))
ax3.text(0.045, 0.90, "Là où le modèle n’est pas rigoureux",
         fontsize=13.5, fontweight="bold", color=CORAIL, transform=ax3.transAxes)

barres = ax3.inset_axes([0.07, 0.42, 0.36, 0.34])
# Positions numeriques : un axe insere herite des unites du parent, et des
# libelles textuels y declenchent une erreur de conversion.
barres.bar([0, 1], [g_chute * 1000, g_rebonds * 1000],
           color=[CORAIL, INDIGO], width=0.55)
barres.set_xticks([0, 1])
barres.set_xticklabels(["chute", "rebonds"])
barres.set_ylabel("g  ($10^{-3}$ px/ms²)", fontsize=9.5)
barres.tick_params(labelsize=9)
barres.set_facecolor("#2A1233")
for cote in barres.spines.values():
    cote.set_color("#6B2E52")
barres.grid(False)
for i, v in enumerate([g_chute * 1000, g_rebonds * 1000]):
    barres.text(i, v + 0.06, f"{v:.2f}", ha="center", fontsize=9.5, color=TEXTE)

ax3.text(0.50, 0.74,
         f"La gravité n’est pas la même\ndes deux côtés : facteur "
         f"{g_rebonds / g_chute:.1f}.",
         fontsize=12, color=TEXTE, transform=ax3.transAxes, va="top")

ax3.text(0.05, 0.31,
         "Deux contraintes incompatibles : la vitesse doit se raccorder\n"
         "à l’impact, et les six rebonds doivent tenir dans 1550 ms.\n\n"
         "Avec la gravité de la chute, la séquence durerait\n"
         f"{(2*v0/g_chute) * ((1-REST**N)/(1-REST)):.0f} ms : la bille tombe doucement, puis rebondit sèchement.",
         fontsize=10.5, color=SOURDINE, transform=ax3.transAxes, va="top", linespacing=1.5)

DOSSIER = os.path.dirname(os.path.abspath(__file__))
chemin = os.path.join(DOSSIER, "schema_rebonds.png")
fig.savefig(chemin, facecolor=NUIT)
print("\necrit :", chemin, os.path.getsize(chemin) // 1024, "Ko")
