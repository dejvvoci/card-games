export const environment = {
  production: false,
  // Përdorim WebSocket nativ (jo SockJS) -> path /websocket = transporti "raw" i Spring-it,
  // pa asnjë mbështjellje shtesë. S'kërkohet asnjë polyfill browser-i.
  wsEndpoint: 'ws://localhost:8080/ws-pesekatesh/websocket',
  apiBaseUrl: 'http://localhost:8080/api',
};