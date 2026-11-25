import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Api, Champ, Domaine, Entite, Resultat, messageErreur } from './api';

function ficheVide(domaine: string): Entite {
  return {
    id: null, nom: '', siren: null, commune: null, codePostal: null,
    latitude: null, longitude: null, domaines: domaine ? [domaine] : [], champs: {},
  };
}

/**
 * Recherche et saisie des fiches. Le formulaire n'a aucun champ de domaine écrit en dur :
 * il est construit à partir des champs que l'administrateur a définis dans l'ontologie.
 */
@Component({
  selector: 'app-page-entites',
  imports: [FormsModule],
  template: `
    <h2>Fiches</h2>
    @if (erreur()) { <p class="erreur" role="alert">{{ erreur() }}</p> }

    <section class="carte">
      <form class="ligne" (ngSubmit)="chercher(0)">
        <label class="large">Recherche <input type="search" name="texte" [(ngModel)]="texte"></label>
        <label>Domaine
          <select name="domaine" [(ngModel)]="domaine" (ngModelChange)="changerDomaine()">
            <option value="">Tous</option>
            @for (d of domaines(); track d.id) { <option [value]="d.id">{{ d.libelle }}</option> }
          </select>
        </label>
        @for (f of resultat().facettes; track f.champ) {
          <label>{{ f.libelle }}
            <select [name]="'f-' + f.champ" [ngModel]="filtres[f.champ] ?? ''" (ngModelChange)="filtrer(f.champ, $event)">
              <option value="">Tout</option>
              @for (v of f.valeurs; track v.valeur) { <option [value]="v.valeur">{{ v.valeur }} ({{ v.nombre }})</option> }
            </select>
          </label>
        }
        <button type="submit">Chercher</button>
        <button type="button" class="discret" (click)="nouvelle()">Nouvelle fiche</button>
      </form>

      <table>
        <thead><tr><th>Nom</th><th>Commune</th><th>Domaines</th><th></th></tr></thead>
        <tbody>
          @for (e of resultat().entites; track e.id) {
            <tr [class.choisie]="fiche()?.id === e.id">
              <td>{{ e.nom }}</td>
              <td>{{ e.codePostal }} {{ e.commune }}</td>
              <td>{{ e.domaines.join(', ') }}</td>
              <td class="droite"><button type="button" class="discret" (click)="ouvrir(e)">Modifier</button></td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="aide">Aucun résultat.</td></tr>
          }
        </tbody>
      </table>
      <p class="pied">
        {{ resultat().total }} fiche{{ resultat().total > 1 ? 's' : '' }} · page {{ page() + 1 }} sur {{ pages() }}
        <button type="button" class="discret" [disabled]="page() === 0" (click)="chercher(page() - 1)">Précédent</button>
        <button type="button" class="discret" [disabled]="page() >= pages() - 1" (click)="chercher(page() + 1)">Suivant</button>
      </p>
    </section>

    @if (fiche(); as f) {
      <section class="carte">
        <h3>{{ f.id ? 'Modifier la fiche' : 'Nouvelle fiche' }}</h3>

        @if (!f.id) {
          <form class="ligne" (ngSubmit)="chercherSirene()">
            <label class="large">Préremplir depuis le répertoire Sirene (nom ou SIREN)
              <input name="sirene" [(ngModel)]="texteSirene" minlength="3">
            </label>
            <button type="submit" class="discret">Chercher dans Sirene</button>
          </form>
          @if (propositions().length) {
            <ul class="propositions">
              @for (p of propositions(); track p.siren) {
                <li><button type="button" class="discret" (click)="reprendre(p)">Reprendre</button>
                  {{ p.nom }} · {{ p.siren }} · {{ p.codePostal }} {{ p.commune }}</li>
              }
            </ul>
          }
        }

        <form class="grille" (ngSubmit)="enregistrer()">
          <label class="large">Nom <input name="nom" [(ngModel)]="f.nom" required></label>
          <label>SIREN <input name="siren" [(ngModel)]="f.siren" maxlength="9"></label>
          <label>Code postal <input name="cp" [(ngModel)]="f.codePostal" maxlength="5"></label>
          <label>Commune <input name="commune" [(ngModel)]="f.commune"></label>
          <label>Latitude <input type="number" step="any" name="lat" [(ngModel)]="f.latitude"></label>
          <label>Longitude <input type="number" step="any" name="lon" [(ngModel)]="f.longitude"></label>

          <fieldset class="large">
            <legend>Domaines</legend>
            @for (d of domaines(); track d.id) {
              <label class="case">
                <input type="checkbox" [name]="'dom-' + d.id" [checked]="f.domaines.includes(d.id)"
                       (change)="basculer(f, d.id)"> {{ d.libelle }}
              </label>
            }
          </fieldset>

          @for (c of champsDe(f); track c.id) {
            <label>{{ c.libelle }}
              @switch (c.type) {
                @case ('NOMBRE') { <input type="number" step="1" [name]="c.id" [(ngModel)]="f.champs[c.id]"> }
                @case ('OUI_NON') {
                  <select [name]="c.id" [(ngModel)]="f.champs[c.id]">
                    <option [ngValue]="undefined"></option>
                    <option [ngValue]="true">oui</option>
                    <option [ngValue]="false">non</option>
                  </select>
                }
                @default { <input [name]="c.id" [(ngModel)]="f.champs[c.id]"> }
              }
            </label>
          }

          <p class="large actions">
            <button type="submit">Enregistrer</button>
            <button type="button" class="discret" (click)="fiche.set(null)">Fermer</button>
            @if (f.id) { <button type="button" class="danger" (click)="supprimer(f)">Supprimer</button> }
          </p>
        </form>
      </section>
    }
  `,
})
export class PageEntites {
  private readonly api = inject(Api);

  protected readonly domaines = signal<Domaine[]>([]);
  protected readonly resultat = signal<Resultat>({ total: 0, entites: [], facettes: [] });
  protected readonly page = signal(0);
  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.resultat().total / 20)));
  protected readonly fiche = signal<Entite | null>(null);
  protected readonly propositions = signal<Entite[]>([]);
  protected readonly erreur = signal('');

  protected texte = '';
  protected domaine = '';
  protected filtres: Record<string, string> = {};
  protected texteSirene = '';

  constructor() {
    this.api.domaines().subscribe({
      next: liste => this.domaines.set(liste),
      error: e => this.erreur.set(messageErreur(e)),
    });
    this.chercher(0);
  }

  /** Les champs à afficher : ceux des domaines cochés sur la fiche. */
  protected champsDe(f: Entite): Champ[] {
    return this.domaines().filter(d => f.domaines.includes(d.id)).flatMap(d => d.champs);
  }

  protected changerDomaine(): void {
    this.filtres = {};
    this.chercher(0);
  }

  protected filtrer(champ: string, valeur: string): void {
    this.filtres = { ...this.filtres, [champ]: valeur };
    this.chercher(0);
  }

  protected chercher(page: number): void {
    this.api.chercher(this.texte, this.domaine, this.filtres, page).subscribe({
      next: r => { this.erreur.set(''); this.page.set(page); this.resultat.set(r); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  protected nouvelle(): void {
    this.propositions.set([]);
    this.fiche.set(ficheVide(this.domaine));
  }

  protected ouvrir(e: Entite): void {
    // La fiche est relue dans la base RDF, qui fait foi : la liste vient de l'index et peut dater.
    if (!e.id) return;
    this.api.entite(e.id).subscribe({
      next: f => { this.erreur.set(''); this.fiche.set({ ...f, champs: { ...f.champs } }); },
      error: err => this.erreur.set(messageErreur(err)),
    });
  }

  protected basculer(f: Entite, domaine: string): void {
    const domaines = f.domaines.includes(domaine) ? f.domaines.filter(d => d !== domaine) : [...f.domaines, domaine];
    this.fiche.set({ ...f, domaines });
  }

  protected chercherSirene(): void {
    this.api.sirene(this.texteSirene).subscribe({
      next: liste => { this.erreur.set(''); this.propositions.set(liste); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  protected reprendre(p: Entite): void {
    const f = this.fiche();
    if (!f) return;
    this.fiche.set({ ...f, nom: p.nom, siren: p.siren, commune: p.commune, codePostal: p.codePostal,
      latitude: p.latitude, longitude: p.longitude });
    this.propositions.set([]);
  }

  protected enregistrer(): void {
    const f = this.fiche();
    if (!f) return;
    // Seuls les champs des domaines cochés sont envoyés ; les valeurs vides sont écartées.
    const permis = new Set(this.champsDe(f).map(c => c.id));
    const champs: Record<string, unknown> = {};
    for (const [id, valeur] of Object.entries(f.champs)) {
      if (permis.has(id) && valeur !== null && valeur !== undefined && valeur !== '') champs[id] = valeur;
    }
    this.api.enregistrer({ ...f, champs }).subscribe({
      next: () => { this.fiche.set(null); this.chercher(this.page()); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  protected supprimer(f: Entite): void {
    if (!f.id || !confirm(`Supprimer la fiche « ${f.nom} » ?`)) return;
    this.api.supprimer(f.id).subscribe({
      next: () => { this.fiche.set(null); this.chercher(0); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }
}
