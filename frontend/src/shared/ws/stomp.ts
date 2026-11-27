import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { apiUrl } from "../api/server";

/**
 * Ket noi STOMP cho dashboard realtime cua M1.
 *   const client = connectStomp();
 *   client.onConnect = () => client.subscribe("/topic/hubs", (msg) => ...);
 */
export function connectStomp(): Client {
  const client = new Client({
    webSocketFactory: () => new SockJS(apiUrl("/ws")),
    reconnectDelay: 5000,
  });
  client.activate();
  return client;
}
