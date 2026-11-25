import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Api, Domaine, Widget, messageErreur } from './api';

/**
 * Configuration des widgets : type, domaine, champs publiés, sites autorisés.
 * L'écran donne ensuite les deux lignes à coller dans le site partenaire.
 */
@Component({
  selector: 'app-page-widgets',
  imports: [FormsModule],
  template: `
    <h2>Widgets</h2>
    <p class="aide">Un widget ne publie que les champs cochés ici, et seulement sur les sites autorisés.</p>
    @if (erreur()) { <p class="erreur" role="alert">{{ erreur() }}</p> }

    @for (w of widgets(); track w.id) {
      <section class="carte">
        <h3>{{ w.nom }} <code>{{ w.type }}</code></h3>
        <p class="aide">Domaine : {{ w.domaine }} · champs publiés : {{ w.champs || 'aucun' }} ·
          sites autorisés : {{ w.origines === '*' ? 'tous' : w.origines }}</p>
        <label>Code à coller dans le site partenaire
          <textarea readonly rows="2" (focus)="selectionner($event)">{{ code(w) }}</textarea>
        </label>
        <p class="actions"><button type="button" class="danger" (click)="supprimer(w)">Supprimer</button></p>
      </section>
    } @empty {
      <p class="aide">Aucun widget.</p>
    }

    <section class="carte">
      <h3>Nouveau widget</h3>
      <form class="grille" (ngSubmit)="creer()">
        <label class="large">Nom <input name="nom" [(ngModel)]="nom" required maxlength="80"></label>
        <label>Type
          <select name="type" [(ngModel)]="type">
            <option value="annuaire">Annuaire</option>
            <option value="carte">Carte</option>
          </select>
        </label>
        <label>Domaine
          <select name="domaine" [(ngModel)]="domaine" (ngModelChange)="choisis.set([])" required>
            @for (d of domaines(); track d.id) { <option [value]="d.id">{{ d.libelle }}</option> }
          </select>
        </label>
        <fieldset class="large">
          <legend>Champs publiés</legend>
          @for (c of champsDuDomaine(); track c.id) {
            <label class="case">
              <input type="checkbox" [name]="'c-' + c.id" [checked]="choisis().includes(c.id)" (change)="basculer(c.id)">
              {{ c.libelle }}
            </label>
          } @empty {
            <span class="aide">Ce domaine n'a pas de champ.</span>
          }
        </fieldset>
        <label class="large">Sites autorisés, séparés par des virgules (vide : tous)
          <input name="origines" [(ngModel)]="origines" placeholder="https://www.exemple.fr">
        </label>
        <p class="large actions"><button type="submit">Créer le widget</button></p>
      </form>
    </section>
  `,
})
export class PageWidgets {
  private readonly api = inject(Api);

  protected readonly widgets = signal<Widget[]>([]);
  protected readonly domaines = signal<Domaine[]>([]);
  protected readonly choisis = signal<string[]>([]);
  protected readonly erreur = signal('');

  protected nom = '';
  protected type = 'annuaire';
  protected domaine = '';
  protected origines = '';

  protected readonly champsDuDomaine = computed(() => {
    // La lecture de « choisis » relance le calcul quand le domaine change (il vide la sélection).
    this.choisis();
    return this.domaines().find(d => d.id === this.domaine)?.champs ?? [];
  });

  constructor() {
    this.api.domaines().subscribe({
      next: liste => { this.domaines.set(liste); this.domaine = liste[0]?.id ?? ''; this.choisis.set([]); },
      error: e => this.erreur.set(messageErreur(e)),
    });
    this.charger();
  }

  protected code(w: Widget): string {
    return `<script src="${location.origin}/widget.js" defer></script>\n<annuaire-widget widget="${w.id}"></annuaire-widget>`;
  }

  protected selectionner(evenement: Event): void {
    (evenement.target as HTMLTextAreaElement).select();
  }

  protected basculer(champ: string): void {
    this.choisis.update(liste => (liste.includes(champ) ? liste.filter(c => c !== champ) : [...liste, champ]));
  }

  protected creer(): void {
    const origines = this.origines.split(',').map(o => o.trim()).filter(Boolean);
    this.api.creerWidget({ nom: this.nom, type: this.type, domaine: this.domaine, champs: this.choisis(), origines }).subscribe({
      next: () => { this.nom = ''; this.origines = ''; this.choisis.set([]); this.charger(); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  protected supprimer(w: Widget): void {
    if (!confirm(`Supprimer le widget « ${w.nom} » ? Les sites qui l'affichent ne recevront plus de données.`)) return;
    this.api.supprimerWidget(w.id).subscribe({
      next: () => this.charger(),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  private charger(): void {
    this.api.widgets().subscribe({
      next: liste => { this.erreur.set(''); this.widgets.set(liste); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }
}
