import { useEffect, useState } from "react";
import { vehicleApi, type Vehicle } from "../api/vehicleApi";

/** MAU trang cho moi module: goi API qua client chung, dung token mau cua design system. */
export default function VehicleListPage() {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    vehicleApi.list().then(setVehicles).finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Dang tai...</p>;

  return (
    <div>
      <h1 className="text-xl font-bold text-brand mb-4">Danh sach xe thue (M3)</h1>
      <table className="w-full bg-white rounded shadow text-sm">
        <thead className="bg-slate-100 text-left">
          <tr><th className="p-2">Ma xe</th><th>Loai</th><th>Pin</th><th>Trang thai</th><th>Hub</th></tr>
        </thead>
        <tbody>
          {vehicles.map((v) => (
            <tr key={v.id} className="border-t">
              <td className="p-2 font-medium">{v.code}</td>
              <td>{v.type}</td>
              <td>{v.batteryLevel}%</td>
              <td>{v.state}</td>
              <td>{v.hubId ?? "-"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
