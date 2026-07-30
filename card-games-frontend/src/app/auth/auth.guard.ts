import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Bllokon aksesin te rrugët e lojës nëse s'ka përdorues të loguar (mbron edhe navigimin direkt me URL).
 * Përjashtim: një link ftese (?room=KODI) lejon hyrjen si "mysafir" me emër të përkohshëm, pa llogari —
 * vetëm për atë lojë; navigimi direkt (pa ?room=) kërkon gjithmonë llogari.
 */
export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) return true;
  if (route.queryParamMap.has('room')) return true;
  return router.parseUrl('/');
};
