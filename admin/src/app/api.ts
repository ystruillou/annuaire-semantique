import { HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';

export type TypeChamp = 'TEXTE' | 'NOMBRE' | 'OUI_NON';

export interface Champ { id: string; libelle: string; type: TypeChamp; facette: boolean; ordre: number; }
export interface Domaine { id: string; libelle: string; champs: Champ[]; }
export interface Entite {
  id: string | null; nom: string; siren: string | null; commune: string | null; codePostal: string | null;
  latitude: number | null; longitude: number | null; domaines: string[]; champs: Record<string, unknown>;
}
export interface Facette { champ: string; libelle: string; valeurs: { valeur: string; nombre: number }[]; }
export interface Resultat { total: number; entites: Entite[]; facettes: Facette[]; }
export interface Widget { id: string; nom: string; type: 'annuaire' | 'carte'; domaine: string; champs: string; origines: string; }

const CLE = 'annuaire.jeton';

/**
 * Le jeton d'administration, gardé le temps de l'onglet (sessionStorage) : il disparaît à sa fermeture.
 * Il n'est jamais écrit dans le code ni dans l'adresse de la page.
 */
@Injectable({ providedIn: 'root' })
export class Session {
  readonly jeton = signal(sessionStorage.getItem(CLE) ?? '');

  definir(jeton: string): void {
    const propre = jeton.trim();
    if (propre) {
      sessionStorage.setItem(CLE, propre);
    } else {
      sessionStorage.removeItem(CLE);
    }
    this.jeton.set(propre);
  }
}

/** Ajoute le jeton aux seules requêtes d'écriture vers l'API de l'application. */
export const ajouterJeton: HttpInterceptorFn = (requete, suite) => {
  const jeton = inject(Session).jeton();
  const ecriture = !['GET', 'HEAD', 'OPTIONS'].includes(requete.method);
  if (jeton && ecriture && requete.url.startsWith('/api/')) {
    return suite(requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }));
  }
  return suite(requete);
};

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  domaines(): Observable<Domaine[]> {
    return this.http.get<Domaine[]>('/api/domaines');
  }

  creerDomaine(libelle: string): Observable<Domaine> {
    return this.http.post<Domaine>('/api/domaines', { libelle });
  }

  ajouterChamp(domaine: string, champ: { libelle: string; type: TypeChamp; facette: boolean }): Observable<Domaine> {
    return this.http.post<Domaine>(`/api/domaines/${encodeURIComponent(domaine)}/champs`, champ);
  }

  retirerChamp(domaine: string, champ: string): Observable<Domaine> {
    return this.http.delete<Domaine>(`/api/domaines/${encodeURIComponent(domaine)}/champs/${encodeURIComponent(champ)}`);
  }

  chercher(texte: string, domaine: string, filtres: Record<string, string>, page: number): Observable<Resultat> {
    let params = new HttpParams().set('page', page).set('taille', 20);
    if (texte) params = params.set('q', texte);
    if (domaine) params = params.set('domaine', domaine);
    for (const [champ, valeur] of Object.entries(filtres)) {
      if (valeur) params = params.set('f.' + champ, valeur);
    }
    return this.http.get<Resultat>('/api/recherche', { params });
  }

  entite(id: string): Observable<Entite> {
    return this.http.get<Entite>(`/api/entites/${encodeURIComponent(id)}`);
  }

  enregistrer(entite: Entite): Observable<Entite> {
    return entite.id
      ? this.http.put<Entite>(`/api/entites/${encodeURIComponent(entite.id)}`, entite)
      : this.http.post<Entite>('/api/entites', entite);
  }

  supprimer(id: string): Observable<void> {
    return this.http.delete<void>(`/api/entites/${encodeURIComponent(id)}`);
  }

  sirene(texte: string): Observable<Entite[]> {
    return this.http.get<Entite[]>('/api/sirene', { params: new HttpParams().set('q', texte) });
  }

  widgets(): Observable<Widget[]> {
    return this.http.get<Widget[]>('/api/widgets');
  }

  creerWidget(w: { nom: string; type: string; domaine: string; champs: string[]; origines: string[] }): Observable<Widget> {
    return this.http.post<Widget>('/api/widgets', w);
  }

  supprimerWidget(id: string): Observable<void> {
    return this.http.delete<void>(`/api/widgets/${encodeURIComponent(id)}`);
  }
}

/** Message lisible à partir d'une réponse d'erreur de l'API (format « problem+json »). */
export function messageErreur(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 401) return "Jeton d'administration absent ou refusé.";
    if (e.status === 0) return 'Le serveur ne répond pas.';
    const detail = (e.error as { detail?: string } | null)?.detail;
    return detail ?? `Erreur ${e.status}.`;
  }
  return 'Erreur inattendue.';
}
