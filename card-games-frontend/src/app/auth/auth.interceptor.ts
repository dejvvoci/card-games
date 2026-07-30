import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Nëse serveri kthen 401 (token i skaduar/pavlefshëm — p.sh. AuthTokenService e ruan token-in vetëm
 * në memorie, kështu që çdo rindezje e backend-it e "harron"), dil automatikisht nga llogaria dhe
 * kthehu te ekrani i hyrjes — në vend që kërkesat (p.sh. statistikat) të dështojnë heshtazi dhe UI-ja
 * të mbetet e "ngrirë" në "Duke ngarkuar...".
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  return next(req).pipe(
    catchError((err) => {
      if (err?.status === 401 && auth.isLoggedIn()) {
        auth.logout();
      }
      return throwError(() => err);
    }),
  );
};
