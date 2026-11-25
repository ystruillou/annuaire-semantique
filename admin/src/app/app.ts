import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Session } from './api';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  template: `
    <header class="bandeau">
      <div class="conteneur">
        <h1>Annuaire sémantique <span>administration</span></h1>
        <nav aria-label="Rubriques">
          <a routerLink="/domaines" routerLinkActive="actif">Domaines et champs</a>
          <a routerLink="/fiches" routerLinkActive="actif">Fiches</a>
          <a routerLink="/widgets" routerLinkActive="actif">Widgets</a>
          <a href="/demo.html" target="_blank" rel="noopener">Site partenaire (démonstration)</a>
        </nav>
      </div>
    </header>

    <div class="conteneur">
      <form class="jeton" (ngSubmit)="valider()">
        @if (session.jeton()) {
          <span>Jeton d'administration enregistré pour cet onglet.</span>
          <button type="button" class="discret" (click)="session.definir('')">Oublier</button>
        } @else {
          <label for="jeton">Jeton d'administration, nécessaire pour modifier</label>
          <input id="jeton" type="password" name="jeton" [(ngModel)]="saisie" autocomplete="off">
          <button type="submit">Utiliser</button>
        }
      </form>
      <main><router-outlet /></main>
    </div>
  `,
})
export class App {
  protected readonly session = inject(Session);
  protected saisie = '';

  protected valider(): void {
    this.session.definir(this.saisie);
    this.saisie = '';
  }
}
