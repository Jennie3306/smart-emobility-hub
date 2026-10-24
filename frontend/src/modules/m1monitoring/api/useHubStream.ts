import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { Alert, HubStatus } from './types';

/**
 * FR-M1-06. Dang ky mot lan vao /topic/hubs va /topic/alerts roi doi.
 * Ngoai le 6a/7a: mat ket noi thi bao "du lieu co the da cu" va quay ve
 * goi lai API moi 15 giay cho den khi ket noi lai duoc.
 */
export function useHubStream(onHub: (s: HubStatus) => void,
                             onAlert: (a: Alert) => void,
                             onPoll: () => void) {

  const [live, setLive] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());
  const pollTimer = useRef<number | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        setLive(true);
        stopPolling();
        client.subscribe('/topic/hubs', msg => {
          onHub(JSON.parse(msg.body));
          setLastUpdate(new Date());
        });
        client.subscribe('/topic/alerts', msg => {
          onAlert(JSON.parse(msg.body));
          setLastUpdate(new Date());
        });
      },
      onWebSocketClose: () => { setLive(false); startPolling(); },
      onStompError: () => { setLive(false); startPolling(); },
    });

    function startPolling() {
      if (pollTimer.current !== null) return;
      pollTimer.current = window.setInterval(onPoll, 15000);   // 15 giay
    }
    function stopPolling() {
      if (pollTimer.current !== null) {
        window.clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
    }

    client.activate();
    return () => { stopPolling(); client.deactivate(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { live, lastUpdate };
}
