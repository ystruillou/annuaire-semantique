import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';

import { ajouterJeton } from './api';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Adresses en « #/… » : le serveur n'a qu'un fichier à servir, sans règle de réécriture.
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([ajouterJeton])),
  ],
};
