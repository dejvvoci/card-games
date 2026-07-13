import { Directive, EventEmitter, HostListener, Output } from '@angular/core';

/**
 * Gjest touch i thjeshtë: "fshi lart" (swipe up) mbi një element — përdoret te letrat e dorës
 * si alternativë e shpejtë ndaj klikimit (p.sh. për ta hedhur/luajtur letrën me një gisht).
 * Nuk zëvendëson klikimin — funksionon njëkohësisht me të, pa konflikt (tap i shkurtër = click,
 * lëvizje e mjaftueshme vertikalisht lart = appSwipeUp).
 */
@Directive({
  selector: '[appSwipeUp]',
  standalone: true,
})
export class SwipeUpDirective {
  @Output() appSwipeUp = new EventEmitter<void>();

  private static readonly MIN_DISTANCE = 40;

  private startX = 0;
  private startY = 0;
  private tracking = false;

  @HostListener('touchstart', ['$event'])
  onTouchStart(event: TouchEvent): void {
    if (event.touches.length !== 1) return;
    this.startX = event.touches[0].clientX;
    this.startY = event.touches[0].clientY;
    this.tracking = true;
  }

  @HostListener('touchend', ['$event'])
  onTouchEnd(event: TouchEvent): void {
    if (!this.tracking) return;
    this.tracking = false;
    const touch = event.changedTouches[0];
    const dx = touch.clientX - this.startX;
    const dy = touch.clientY - this.startY;
    // Lart = dy negativ; kërkojmë lëvizje kryesisht vertikale, jo diagonale/horizontale
    if (-dy > SwipeUpDirective.MIN_DISTANCE && Math.abs(dx) < Math.abs(dy)) {
      this.appSwipeUp.emit();
    }
  }

  @HostListener('touchcancel')
  onTouchCancel(): void {
    this.tracking = false;
  }
}
