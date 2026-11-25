import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Api, Champ, Domaine, TypeChamp, messageErreur } from './api';

interface BrouillonChamp { libelle: string; type: TypeChamp; facette: boolean; }

/**
 * L'écran où l'administrateur fait évoluer l'ontologie : il crée un domaine d'intérêt et y ajoute
 * des champs. Chaque ajout écrit quelques triplets SHACL dans la base ; rien n'est redéployé.
 */
@Component({
  selector: 'app-page-domaines',
  imports: [FormsModule],
  template: `
    <h2>Domaines d'intérêt et champs</h2>
    <p class="aide">Un champ ajouté ici apparaît aussitôt dans le formulaire des fiches et dans la recherche.
      Marqué « filtre », il devient aussi une facette, dans la recherche comme dans les widgets.</p>

    @if (erreur()) { <p class="erreur" role="alert">{{ erreur() }}</p> }

    @for (d of domaines(); track d.id) {
      <section class="carte">
        <h3>{{ d.libelle }} <code>{{ d.id }}</code></h3>
        @if (d.champs.length) {
          <table>
            <thead><tr><th>Champ</th><th>Type</th><th>Filtre</th><th>Identifiant</th><th></th></tr></thead>
            <tbody>
              @for (c of d.champs; track c.id) {
                <tr>
                  <td>{{ c.libelle }}</td>
                  <td>{{ libelleType(c.type) }}</td>
                  <td>{{ c.facette ? 'oui' : '' }}</td>
                  <td><code>{{ c.id }}</code></td>
                  <td class="droite"><button type="button" class="discret" (click)="retirer(d, c)">Retirer</button></td>
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <p class="aide">Aucun champ.</p>
        }
        <form class="ligne" (ngSubmit)="ajouter(d)">
          <label>Nouveau champ
            <input [name]="'libelle-' + d.id" [(ngModel)]="brouillon(d).libelle" required maxlength="80">
          </label>
          <label>Type
            <select [name]="'type-' + d.id" [(ngModel)]="brouillon(d).type">
              <option value="TEXTE">Texte</option>
              <option value="NOMBRE">Nombre entier</option>
              <option value="OUI_NON">Oui / non</option>
            </select>
          </label>
          <label class="case">
            <input type="checkbox" [name]="'facette-' + d.id" [(ngModel)]="brouillon(d).facette"
                   [disabled]="brouillon(d).type !== 'TEXTE'">
            Filtre
          </label>
          <button type="submit">Ajouter le champ</button>
        </form>
      </section>
    } @empty {
      <p class="aide">Aucun domaine pour l'instant.</p>
    }

    <section class="carte">
      <h3>Nouveau domaine</h3>
      <form class="ligne" (ngSubmit)="creer()">
        <label>Libellé <input name="domaine" [(ngModel)]="nouveauDomaine" required maxlength="80"></label>
        <button type="submit">Créer le domaine</button>
      </form>
    </section>
  `,
})
export class PageDomaines {
  private readonly api = inject(Api);

  protected readonly domaines = signal<Domaine[]>([]);
  protected readonly erreur = signal('');
  protected nouveauDomaine = '';
  private readonly brouillons = new Map<string, BrouillonChamp>();

  constructor() {
    this.charger();
  }

  protected brouillon(d: Domaine): BrouillonChamp {
    let b = this.brouillons.get(d.id);
    if (!b) {
      b = { libelle: '', type: 'TEXTE', facette: false };
      this.brouillons.set(d.id, b);
    }
    return b;
  }

  protected libelleType(t: TypeChamp): string {
    return t === 'TEXTE' ? 'Texte' : t === 'NOMBRE' ? 'Nombre entier' : 'Oui / non';
  }

  protected creer(): void {
    this.api.creerDomaine(this.nouveauDomaine).subscribe({
      next: () => { this.nouveauDomaine = ''; this.charger(); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  protected ajouter(d: Domaine): void {
    const b = this.brouillon(d);
    this.api.ajouterChamp(d.id, { ...b, facette: b.facette && b.type === 'TEXTE' }).subscribe({
      next: maj => { this.brouillons.delete(d.id); this.remplacer(maj); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  protected retirer(d: Domaine, c: Champ): void {
    if (!confirm(`Retirer le champ « ${c.libelle} » et ses valeurs sur toutes les fiches ?`)) return;
    this.api.retirerChamp(d.id, c.id).subscribe({
      next: maj => this.remplacer(maj),
      error: e => this.erreur.set(messageErreur(e)),
    });
  }

  private remplacer(maj: Domaine): void {
    this.erreur.set('');
    this.domaines.update(liste => liste.map(x => (x.id === maj.id ? maj : x)));
  }

  private charger(): void {
    this.api.domaines().subscribe({
      next: liste => { this.erreur.set(''); this.domaines.set(liste); },
      error: e => this.erreur.set(messageErreur(e)),
    });
  }
}
