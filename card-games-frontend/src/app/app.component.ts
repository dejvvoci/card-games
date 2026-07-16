import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  constructor(private swUpdate: SwUpdate) {}

  ngOnInit(): void {
    if (!this.swUpdate.isEnabled) return;

    // Sapo të jetë gati një version i ri i aplikacionit, aktivizoje dhe rifresko menjëherë —
    // pa këtë, pajisja mund të vazhdojë të shërbejë kodin e vjetër të "cache"-uar pafundësisht,
    // edhe pse serveri ka gjithmonë versionin e fundit (kjo shkaktoi konfuzion me rregullime
    // që "s'dukeshin" në telefon, ndonëse ishin live në server).
    this.swUpdate.versionUpdates
      .pipe(filter((evt): evt is VersionReadyEvent => evt.type === 'VERSION_READY'))
      .subscribe(() => {
        this.swUpdate.activateUpdate().then(() => document.location.reload());
      });

    // Kontrollo për versione të reja menjëherë (jo vetëm pas vonesës default të regjistrimit), dhe çdo minutë
    this.swUpdate.checkForUpdate();
    setInterval(() => this.swUpdate.checkForUpdate(), 60_000);
  }
}
