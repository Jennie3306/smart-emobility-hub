import axios from 'axios';
import { englishHubLabels } from '@/shared/api/hubLabels';
import type { Alert, Dashboard, HubDetail, HubSummary } from './types';

// withCredentials: xac thuc bang session cookie (JSESSIONID), khong dung JWT.
const http = axios.create({ baseURL: '/api/m1', withCredentials: true });

export const monitoringApi = {
  getHubs: (filter?: { hasVehicle?: boolean; hasFreeSlot?: boolean }) =>
    http.get<HubSummary[]>('/hubs', { params: filter }).then(r => englishHubLabels(r.data)),

  getHub: (hubId: number) =>
    http.get<HubDetail>(`/hubs/${hubId}`).then(r => englishHubLabels(r.data)),

  getDashboard: () =>
    http.get<Dashboard>('/dashboard').then(r => englishHubLabels(r.data)),

  getAlertHistory: (params: { from?: string; to?: string; hubId?: number }) =>
    http.get<Alert[]>('/alerts', { params }).then(r => englishHubLabels(r.data)),

  acknowledge: (alertId: number) =>
    http.post<Alert>(`/alerts/${alertId}/acknowledge`).then(r => englishHubLabels(r.data)),

  dismiss: (alertId: number, reason: string) =>
    http.post<Alert>(`/alerts/${alertId}/dismiss`, { reason }).then(r => englishHubLabels(r.data)),
};
