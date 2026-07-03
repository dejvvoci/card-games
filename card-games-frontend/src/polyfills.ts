// Nevojitet sepse `sockjs-client` (varësi e @stomp/stompjs) është shkruar duke
// pritur mjedis Node.js, ku ekziston objekti global `global`. Browser-at (dhe
// bundler-i esbuild i Angular CLI 17+) s'e kanë këtë, prandaj e simulojmë këtu,
// para se çdo modul tjetër (përfshi sockjs-client) të ngarkohet.
(window as any).global = window;