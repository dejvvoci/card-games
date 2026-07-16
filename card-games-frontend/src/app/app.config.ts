import { ApplicationConfig, isDevMode } from "@angular/core";
import { provideRouter } from "@angular/router";
import { provideHttpClient } from "@angular/common/http";
import { routes } from "./app.routes";
import { provideServiceWorker } from "@angular/service-worker";

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
    provideServiceWorker("ngsw-worker.js", {
      enabled: !isDevMode(),
      // Regjistrohet më shpejt (5s) — kështu kontrolli për versione të reja (app.component.ts) nis më herët
      registrationStrategy: "registerWhenStable:5000",
    }),
  ],
};
