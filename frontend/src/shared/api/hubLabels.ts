const labels:Record<string,string>={"Hub Ga Metro":"Metro Hub","Hub KTX Khu A":"Dormitory A Hub","Hub KTX Khu B":"Dormitory B Hub","Hub BK":"Bach Khoa Hub","Hub KHTN":"Science Hub",
  "Ga Metro so 1 - DHQG":"Metro Station 1 · VNU-HCM","Ky tuc xa Khu A":"Dormitory Area A","Ky tuc xa Khu B":"Dormitory Area B",
  "Truong Dai hoc Bach Khoa":"HCMC University of Technology","Truong DH Khoa hoc Tu nhien":"University of Science"};
/** Display aliases only; IDs and stored operational names remain unchanged. */
export function englishHubLabels<T>(data:T):T {
  if(typeof data==="string") return (labels[data]||data) as T;
  if(Array.isArray(data)) return data.map(englishHubLabels) as T;
  if(data&&typeof data==="object") return Object.fromEntries(Object.entries(data).map(([key,value])=>[key,englishHubLabels(value)])) as T;
  return data;
}
